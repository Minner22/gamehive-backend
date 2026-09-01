package pl.m22.gamehive.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.common.persistence.ModeratedLongEntity;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.model.Author;
import pl.m22.gamehive.game.model.Category;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.model.Mechanic;
import pl.m22.gamehive.game.model.Publisher;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.repository.AuthorRepository;
import pl.m22.gamehive.game.repository.CategoryRepository;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.game.repository.MechanicRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;

import java.util.List;
import java.util.UUID;

/**
 * Seed biblioteki gier dla profilu {@code dev} — 18 pozycji APPROVED plus trzy zgłoszenia zwykłego
 * użytkownika w stanach roboczych (PENDING/REJECTED/DRAFT), żeby kolejka moderacji i widok
 * „moje zgłoszenia” miały co pokazać po świeżym {@code docker compose down -v}.
 * <p>
 * Świadomie omija {@code GameSubmissionService}/{@code GameModerationService}: to fixture, a nie
 * decyzja moderatora, więc nie powstaje wpis w {@code content_moderation_audit_log} i nie leci
 * {@code SearchIndexEvent} — po zasianiu trzeba odpalić {@code POST /api/v1/admin/search/reindex},
 * żeby Meilisearch zobaczył te gry. Reindeks nie może iść stąd: {@code ApplicationRunner} biegnie
 * przed {@code ApplicationReadyEvent}, na którym wisi {@code MeiliIndexInitializer}, więc trafiłby
 * w indeks bez ustawień.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevGameSeeder {

    private static final String COVER_URL_TEMPLATE = "https://picsum.photos/seed/%s/600/400";

    private final GameRepository gameRepository;
    private final GameExpansionRepository expansionRepository;
    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final MechanicRepository mechanicRepository;

    /**
     * Dosiewa brakujące gry. Wołane z {@link DevDataInitializer}, więc działa w jego transakcji.
     * Guard idzie po tytule, a nie po „czy tabela jest pusta”, więc niekompletny seed się uzupełnia —
     * kosztem tego, że gra skasowana ręcznie w dev wraca przy następnym starcie.
     *
     * @param adminId id konta admina — właściciel i recenzent zatwierdzonych pozycji
     * @param userId  id zwykłego użytkownika — właściciel zgłoszeń w stanach roboczych
     */
    public void seed(UUID adminId, UUID userId) {

        int games = 0;

        for (GameSeed seed : GAMES) {
            if (!gameRepository.findByTitle(seed.title()).isEmpty()) {
                continue;
            }

            gameRepository.save(toGame(seed, adminId, userId));
            games++;
        }

        // dodatki dopiero po grach: baza musi już istnieć, a rozwiązujemy ją po tytule
        int expansions = 0;

        for (ExpansionSeed seed : EXPANSIONS) {
            if (!expansionRepository.findByName(seed.name()).isEmpty()) {
                continue;
            }

            expansionRepository.save(toExpansion(seed, adminId, userId));
            expansions++;
        }

        if (games + expansions > 0) {
            log.info("Dev content initialized: {} games, {} expansions added"
                    + " - run POST /api/v1/admin/search/reindex to index them", games, expansions);
        }
    }

    private Game toGame(GameSeed seed, UUID adminId, UUID userId) {

        Game game = Game.builder()
                .title(seed.title())
                .description(seed.description())
                .submittedBy(ownerId(seed.owner(), adminId, userId))
                .moderationStatus(initialStatus(seed.status()))
                .minPlayers(seed.minPlayers())
                .maxPlayers(seed.maxPlayers())
                .playingTimeMinutes(seed.playingTimeMinutes())
                .yearPublished(seed.yearPublished())
                .minAge(seed.minAge())
                .coverImageUrl(COVER_URL_TEMPLATE.formatted(seed.coverSeed()))
                .build();

        // wydawcy i autorzy tworzeni „w locie” dzielą los gry: przy niezatwierdzonym zgłoszeniu zostają
        // PENDING, dokładnie jak przy zgłoszeniu przez API (GameContentWriter + kaskada z approve)
        TaxonomyStatus taxonomyStatus = seed.status() == ModerationStatus.APPROVED
                ? TaxonomyStatus.APPROVED
                : TaxonomyStatus.PENDING;

        seed.publishers().forEach(name -> game.addPublisher(findOrCreatePublisher(name, taxonomyStatus)));
        seed.categories().forEach(name -> game.addCategory(requireCategory(name)));
        seed.mechanics().forEach(name -> game.addMechanic(requireMechanic(name)));
        seed.authors().forEach(seedAuthor -> game.addAuthor(findOrCreateAuthor(seedAuthor, taxonomyStatus)));

        applyReviewOutcome(game, seed.status(), seed.rejectionReason(), adminId);

        return game;
    }

    private GameExpansion toExpansion(ExpansionSeed seed, UUID adminId, UUID userId) {

        Game baseGame = gameRepository.findByTitle(seed.baseTitle()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing seeded base game: " + seed.baseTitle()));

        GameExpansion expansion = GameExpansion.builder()
                .baseGame(baseGame)
                .name(seed.name())
                .description(seed.description())
                .submittedBy(ownerId(seed.owner(), adminId, userId))
                .moderationStatus(initialStatus(seed.status()))
                .minPlayers(seed.minPlayers())
                .maxPlayers(seed.maxPlayers())
                .playingTimeMinutes(seed.playingTimeMinutes())
                .minAge(seed.minAge())
                .build();

        seed.categories().forEach(name -> expansion.addCategory(requireCategory(name)));
        seed.mechanics().forEach(name -> expansion.addMechanic(requireMechanic(name)));

        applyReviewOutcome(expansion, seed.status(), seed.rejectionReason(), adminId);

        return expansion;
    }

    // konstruktor ModeratedLongEntity przyjmuje wyłącznie DRAFT/PENDING — do stanów po decyzji
    // moderatora dochodzimy metodami encji, tak jak robi to GameModerationService
    private static ModerationStatus initialStatus(ModerationStatus target) {

        return target == ModerationStatus.DRAFT ? ModerationStatus.DRAFT : ModerationStatus.PENDING;
    }

    private static void applyReviewOutcome(ModeratedLongEntity entity, ModerationStatus status,
                                           String rejectionReason, UUID adminId) {

        if (status == ModerationStatus.APPROVED) {
            entity.approve(adminId);
        } else if (status == ModerationStatus.REJECTED) {
            entity.reject(rejectionReason, adminId);
        }
    }

    private static UUID ownerId(Owner owner, UUID adminId, UUID userId) {

        return owner == Owner.USER ? userId : adminId;
    }

    private Publisher findOrCreatePublisher(String name, TaxonomyStatus status) {

        return publisherRepository.findByName(name)
                .orElseGet(() -> publisherRepository.save(Publisher.of(name, status)));
    }

    private Author findOrCreateAuthor(AuthorSeed seedAuthor, TaxonomyStatus status) {

        return authorRepository.findByFirstNameAndLastName(seedAuthor.firstName(), seedAuthor.lastName())
                .orElseGet(() -> authorRepository.save(
                        Author.of(seedAuthor.firstName(), seedAuthor.lastName(), status)));
    }

    // kategorie i mechaniki są kuratorowane (seed V7) — brak wpisu to błąd fixture'u, nie stan do naprawienia
    private Category requireCategory(String name) {

        return categoryRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Missing seeded category: " + name));
    }

    private Mechanic requireMechanic(String name) {

        return mechanicRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Missing seeded mechanic: " + name));
    }

    private enum Owner {
        ADMIN, USER
    }

    private record AuthorSeed(String firstName, String lastName) {
    }

    private record GameSeed(String title, String description, String coverSeed,
                            int minPlayers, int maxPlayers, int playingTimeMinutes, int yearPublished, int minAge,
                            List<String> publishers, List<String> categories, List<String> mechanics,
                            List<AuthorSeed> authors, Owner owner, ModerationStatus status, String rejectionReason) {
    }

    /**
     * Wartości nadpisań są {@code Integer}, bo {@code null} znaczy „dziedzicz z gry bazowej” (#120);
     * pusta lista kategorii/mechanik znaczy to samo dla taksonomii.
     */
    private record ExpansionSeed(String baseTitle, String name, String description,
                                 Integer minPlayers, Integer maxPlayers, Integer playingTimeMinutes, Integer minAge,
                                 List<String> categories, List<String> mechanics,
                                 Owner owner, ModerationStatus status, String rejectionReason) {
    }

    private static AuthorSeed author(String firstName, String lastName) {

        return new AuthorSeed(firstName, lastName);
    }

    private static GameSeed approved(String title, String description, String coverSeed,
                                     int minPlayers, int maxPlayers, int playingTimeMinutes,
                                     int yearPublished, int minAge, String publisher,
                                     List<String> categories, List<String> mechanics, AuthorSeed gameAuthor) {

        return new GameSeed(title, description, coverSeed, minPlayers, maxPlayers, playingTimeMinutes, yearPublished,
                minAge, List.of(publisher), categories, mechanics, List.of(gameAuthor), Owner.ADMIN,
                ModerationStatus.APPROVED, null);
    }

    private static GameSeed submission(ModerationStatus status, String rejectionReason,
                                       String title, String description, String coverSeed,
                                       int minPlayers, int maxPlayers, int playingTimeMinutes,
                                       int yearPublished, int minAge, String publisher,
                                       List<String> categories, List<String> mechanics, AuthorSeed gameAuthor) {

        return new GameSeed(title, description, coverSeed, minPlayers, maxPlayers, playingTimeMinutes, yearPublished,
                minAge, List.of(publisher), categories, mechanics, List.of(gameAuthor), Owner.USER,
                status, rejectionReason);
    }

    private static ExpansionSeed expansion(String baseTitle, String name, String description,
                                           Integer minPlayers, Integer maxPlayers, Integer playingTimeMinutes,
                                           Integer minAge, List<String> categories, List<String> mechanics) {

        return new ExpansionSeed(baseTitle, name, description, minPlayers, maxPlayers, playingTimeMinutes, minAge,
                categories, mechanics, Owner.ADMIN, ModerationStatus.APPROVED, null);
    }

    private static ExpansionSeed expansionSubmission(ModerationStatus status, String rejectionReason,
                                                     String baseTitle, String name, String description,
                                                     Integer minPlayers, Integer maxPlayers,
                                                     Integer playingTimeMinutes, Integer minAge,
                                                     List<String> categories, List<String> mechanics) {

        return new ExpansionSeed(baseTitle, name, description, minPlayers, maxPlayers, playingTimeMinutes, minAge,
                categories, mechanics, Owner.USER, status, rejectionReason);
    }

    private static final List<GameSeed> GAMES = List.of(

            approved("Agricola",
                    "Zbuduj gospodarstwo: obsiewaj pola, hoduj zwierzęta i wyżyw rodzinę. Klasyk stawiania robotników.",
                    "agricola", 1, 5, 120, 2007, 12,
                    "Lookout Games", List.of("Strategy"), List.of("Worker Placement"),
                    author("Uwe", "Rosenberg")),

            approved("Wingspan",
                    "Przyciągaj ptaki do swoich siedlisk i buduj łańcuchy kombinacji w spokojnej grze o ornitologii.",
                    "wingspan", 1, 5, 70, 2019, 10,
                    "Stonemaier Games", List.of("Family", "Strategy"), List.of("Hand Management"),
                    author("Elizabeth", "Hargrave")),

            approved("Carcassonne",
                    "Dokładaj kafle krajobrazu i obstawiaj miasta, drogi oraz klasztory swoimi meplami.",
                    "carcassonne", 2, 5, 45, 2000, 7,
                    "Hans im Glueck", List.of("Family"), List.of("Area Control"),
                    author("Klaus-Juergen", "Wrede")),

            approved("Osadnicy z Catanu",
                    "Handluj surowcami, buduj drogi i osady, zanim zrobią to inni osadnicy wyspy.",
                    "catan", 3, 4, 90, 1995, 10,
                    "Kosmos", List.of("Family"), List.of("Dice Rolling"),
                    author("Klaus", "Teuber")),

            approved("Dominion",
                    "Kupuj karty do własnej talii i przekuwaj ją w maszynę punktującą szybciej niż przeciwnicy.",
                    "dominion", 2, 4, 30, 2008, 13,
                    "Rio Grande Games", List.of("Deck-building"), List.of("Deck-building"),
                    author("Donald", "Vaccarino")),

            approved("Pandemic",
                    "Wspólnie powstrzymajcie cztery choroby, zanim wymkną się spod kontroli na całym świecie.",
                    "pandemic", 2, 4, 45, 2008, 8,
                    "Z-Man Games", List.of("Cooperative"), List.of("Hand Management"),
                    author("Matt", "Leacock")),

            approved("Azul",
                    "Wybieraj płytki z manufaktur i układaj je w mozaikę, nie zostawiając rywalom tego, czego potrzebują.",
                    "azul", 2, 4, 40, 2017, 8,
                    "Plan B Games", List.of("Abstract"), List.of(),
                    author("Michael", "Kiesling")),

            approved("Splendor",
                    "Zbieraj żetony klejnotów, kupuj kopalnie i przyciągaj arystokratów renesansowego dworu.",
                    "splendor", 2, 4, 30, 2014, 10,
                    "Space Cowboys", List.of("Strategy"), List.of("Hand Management"),
                    author("Marc", "Andre")),

            approved("Terraforming Mars",
                    "Kierujesz korporacją terraformującą Marsa: podnosisz temperaturę, poziom tlenu i zalewasz oceany, "
                            + "grając karty projektów i rozbudowując własną produkcję.",
                    "terraforming-mars", 1, 5, 120, 2016, 12,
                    "FryxGames", List.of("Strategy"), List.of("Hand Management"),
                    author("Jacob", "Fryxelius")),

            approved("Wiedźmin: Stary Świat",
                    "Wciel się w wiedźmina przemierzającego Stary Świat: poluj na potwory, warz eliksiry i rozwijaj "
                            + "szkołę, budując talię umiejętności swojego bohatera.",
                    "wiedzmin-stary-swiat", 1, 5, 120, 2023, 14,
                    "Go on Board", List.of("Strategy"), List.of("Deck-building", "Dice Rolling"),
                    author("Łukasz", "Woźniak")),

            approved("Gloomhaven",
                    "Kampanijne przemierzanie lochów w mrocznym świecie najemników. Zamiast kostek decyduje wybór dwóch "
                            + "kart akcji na rundę, a decyzje drużyny trwale zmieniają mapę kampanii.",
                    "gloomhaven", 1, 4, 120, 2017, 14,
                    "Cephalofair Games", List.of("Strategy", "Cooperative"), List.of("Hand Management"),
                    author("Isaac", "Childres")),

            approved("Brass: Birmingham",
                    "Ekonomiczna gra o rewolucji przemysłowej w Anglii: budujesz kopalnie, huty i przędzalnie, rozwijasz "
                            + "kanały i koleje oraz sprzedajesz towary w dwóch epokach.",
                    "brass-birmingham", 2, 4, 120, 2018, 14,
                    "Roxley", List.of("Strategy"), List.of("Hand Management"),
                    author("Martin", "Wallace")),

            approved("7 Cudów Świata",
                    "Prowadzisz jedno z siedmiu antycznych miast przez trzy epoki, w każdej rundzie wybierając kartę "
                            + "z ręki i przekazując resztę sąsiadowi. Rozgrywka trwa pół godziny niezależnie od liczby graczy.",
                    "7-cudow-swiata", 3, 7, 30, 2010, 10,
                    "Repos Production", List.of("Strategy", "Family"), List.of("Hand Management"),
                    author("Antoine", "Bauza")),

            approved("Wsiąść do pociągu: Europa",
                    "Zbierasz kolorowe karty wagonów, by budować połączenia kolejowe między europejskimi miastami "
                            + "i realizować bilety na trasy. Klasyk rodzinnych gier planszowych.",
                    "wsiasc-do-pociagu-europa", 2, 5, 60, 2005, 8,
                    "Days of Wonder", List.of("Family"), List.of("Hand Management"),
                    author("Alan R.", "Moon")),

            approved("Scythe",
                    "Alternatywne lata dwudzieste w Europie Wschodniej: rozwijasz frakcję, wysyłasz robotników "
                            + "po surowce i prowadzisz mechy do walki o kontrolę nad terenem wokół Fabryki.",
                    "scythe", 1, 5, 115, 2016, 14,
                    "Stonemaier Games", List.of("Strategy"), List.of("Worker Placement", "Area Control"),
                    author("Jamey", "Stegmaier")),

            approved("Everdell",
                    "W leśnej dolinie zwierzęcych osadników wystawiasz robotników i budujesz miasto z kart konstrukcji "
                            + "oraz mieszkańców, ścigając się z upływem czterech pór roku.",
                    "everdell", 1, 4, 80, 2018, 13,
                    "Starling Games", List.of("Strategy", "Family"), List.of("Worker Placement"),
                    author("James A.", "Wilson")),

            approved("Nemesis",
                    "Załoga statku kosmicznego budzi się z hibernacji wśród obcych form życia. Współpraca jest konieczna, "
                            + "ale każdy ma tajny cel, a hałas ściąga potwory z sąsiednich pomieszczeń.",
                    "nemesis", 1, 5, 180, 2018, 12,
                    "Awaken Realms", List.of("Strategy", "Cooperative"), List.of("Dice Rolling"),
                    author("Adam", "Kwapiński")),

            approved("Root",
                    "Asymetryczna wojna o leśne królestwo: Koty ścinają drzewa, Ptaki odbudowują dynastię, Sojusz "
                            + "podsyca bunt, a Włóczęga handluje z każdym. Każda frakcja ma własne zasady i drogę do zwycięstwa.",
                    "root", 2, 4, 90, 2018, 10,
                    "Leder Games", List.of("Strategy"), List.of("Area Control"),
                    author("Cole", "Wehrle")),

            submission(ModerationStatus.PENDING, null,
                    "Ark Nova",
                    "Projektujesz nowoczesne zoo: budujesz wybiegi, sprowadzasz zwierzęta i wspierasz projekty ochrony "
                            + "przyrody, żonglując kartami akcji o zmiennej sile.",
                    "ark-nova", 1, 4, 150, 2021, 14,
                    "Feuerland Spiele", List.of("Strategy"), List.of("Hand Management"),
                    author("Mathias", "Wigge")),

            submission(ModerationStatus.REJECTED, "Opis jest zbyt ogólny — uzupełnij informacje o przebiegu rozgrywki.",
                    "Zamki Burgundii",
                    "Rozbudowujesz księstwo, dokładając heksagonalne kafle do własnej posiadłości. O tym, co możesz "
                            + "zagrać w danej turze, decyduje rzut dwiema kostkami.",
                    "zamki-burgundii", 2, 4, 90, 2011, 12,
                    "Ravensburger", List.of("Strategy"), List.of("Dice Rolling"),
                    author("Stefan", "Feld")),

            submission(ModerationStatus.DRAFT, null,
                    "Cywilizacja: Poprzez Wieki",
                    "Prowadzisz cywilizację od starożytności po współczesność, balansując między nauką, kulturą "
                            + "i armią. Zaniedbana produkcja żywności potrafi zawrócić rozwój o całą epokę.",
                    "poprzez-wieki", 2, 4, 180, 2015, 14,
                    "Czech Games Edition", List.of("Strategy"), List.of("Hand Management"),
                    author("Vlaada", "Chvatil"))
    );

    // Kolejność argumentów: gra bazowa, nazwa, opis, minPlayers, maxPlayers, czas gry, wiek, kategorie, mechaniki.
    // null i pusta lista = dziedziczenie po grze bazowej, więc zestaw celowo pokrywa wszystkie warianty:
    // brak nadpisań, nadpisany pojedynczy licznik, nadpisany czas, nadpisana taksonomia.
    private static final List<ExpansionSeed> EXPANSIONS = List.of(

            expansion("Terraforming Mars", "Terraforming Mars: Preludium",
                    "Karty preludiów dają korporacjom rozpędzony start, przez co partia kończy się wyraźnie szybciej.",
                    null, null, 100, null, List.of(), List.of()),

            expansion("Terraforming Mars", "Terraforming Mars: Wenus Następna",
                    "Nowy tor terraformacji Wenus i karty z nim związane. Zasady rozgrywki pozostają bez zmian.",
                    null, null, null, null, List.of(), List.of()),

            expansion("Wiedźmin: Stary Świat", "Wiedźmin: Stary Świat - Skellige",
                    "Wyspy Skellige dokładają nowy region mapy, morskie podróże i walkę o wpływy między klanami.",
                    null, null, null, null, List.of("Strategy"), List.of("Area Control")),

            expansion("Carcassonne", "Carcassonne: Karczmy i Katedry",
                    "Karczmy przy drogach i katedry w miastach zmieniają punktację, a przy stole robi się miejsce "
                            + "dla szóstego gracza.",
                    null, 6, null, null, List.of(), List.of()),

            expansion("Carcassonne", "Carcassonne: Kupcy i Budowniczowie",
                    "Towary, budowniczy i świnia: kafle dają dodatkowe akcje i premie za rozbudowywane miasta.",
                    null, null, null, null, List.of(), List.of()),

            expansion("Wingspan", "Wingspan: Europa",
                    "Ptaki europejskie z naciskiem na zdolności aktywowane w turze przeciwnika.",
                    null, null, null, null, List.of(), List.of()),

            expansion("Pandemic", "Pandemic: Na Krawędzi",
                    "Nowe role, wydarzenia i wyzwania, w tym mutująca choroba. Do stołu może usiąść piąta osoba.",
                    null, 5, null, null, List.of(), List.of()),

            expansion("Scythe", "Scythe: Inwazja z Odmętów",
                    "Dwie nowe frakcje - Albion i Togawa - rozszerzają konflikt do siedmiu graczy.",
                    null, 7, null, null, List.of(), List.of()),

            expansion("Root", "Root: Rzeczna Ludność",
                    "Wydry handlują z każdym, Jaszczury nawracają leśnych mieszkańców. Więcej frakcji i ostrzejsza "
                            + "polityka przy stole.",
                    null, 6, null, 12, List.of(), List.of()),

            expansionSubmission(ModerationStatus.PENDING, null,
                    "Terraforming Mars", "Terraforming Mars: Kolonie",
                    "Księżyce Jowisza i inne kolonie pozaziemskie jako nowe źródło surowców i handlu.",
                    null, null, null, null, List.of(), List.of()),

            expansionSubmission(ModerationStatus.REJECTED,
                    "Dodatek nie ma opisu zmian w rozgrywce - uzupełnij i wyślij ponownie.",
                    "Wiedźmin: Stary Świat", "Wiedźmin: Stary Świat - Mag",
                    "Nowa postać maga wraz z talią zaklęć i osobnym torem many.",
                    null, null, 150, null, List.of(), List.of()),

            expansionSubmission(ModerationStatus.DRAFT, null,
                    "Dominion", "Dominion: Intryga",
                    "Karty z wyborem i akcjami atakującymi przeciwników, do gry samodzielnie lub w miksie z podstawką.",
                    null, null, null, null, List.of(), List.of())
    );
}
