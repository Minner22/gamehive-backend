package pl.m22.gamehive.game.search;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.repository.*;
import pl.m22.gamehive.game.search.config.MeiliClientConfig;
import pl.m22.gamehive.game.search.service.*;
import pl.m22.gamehive.game.service.GameModerationService;
import pl.m22.gamehive.support.SeededUsers;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH-158: wyszukiwanie na PRAWDZIWYM Meilisearch (Testcontainers). Reszta suity biega na fallbacku
 * ({@code gamehive.search.enabled=false}), więc to jedyne miejsce, gdzie {@code Meili*} — klient, oba gatewaye,
 * ustawienia indeksów, serializacja filtrów i dokumentów — rozmawia z prawdziwym serwerem.
 *
 * <ul>
 *   <li>Baza to H2 z {@code data.sql} (profil {@code test}); PostgreSQL nie jest tu potrzebny.</li>
 *   <li>Executor indeksu pod {@code test} to {@code SyncTaskExecutor}, a gateway po każdym zapisie czeka na zadanie
 *       Meili — gdy {@code approve}/{@code deleteGame} wraca, dokument jest już (nie)zaindeksowany. Bez Awaitility.</li>
 *   <li>Klasa NIE jest {@code @Transactional} (zdarzenia indeksu idą AFTER_COMMIT): własne gry, kasowane przez serwis
 *       w {@code @AfterEach} (to przy okazji usuwa ich dokumenty).</li>
 *   <li>Każdy test zaczyna od reindeksu — indeks zgodny z bazą niezależnie od kolejności testów.</li>
 *   <li>Filtr działa tylko dlatego, że {@code android-json} jest wykluczony z {@code spring-boot-starter-test}
 *       (przesłaniał {@code org.json} SDK i {@code filter(String[])} szedł jako {@code [Ljava.lang.String;@…}).</li>
 * </ul>
 *
 * <p>Tag {@code meilisearch}: poza domyślnym {@code ./mvnw test}, uruchamiany przez {@code -Pintegration}
 * (job {@code integration}). Celowo bez {@code disabledWithoutDocker} — brak Dockera w CI ma czerwienić.
 * Obraz = ten z {@code docker-compose.yml}; przy podbiciu Meilisearch zmień oba miejsca.
 */
@Tag("meilisearch")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "gamehive.search.enabled=true")
class MeilisearchIntegrationTest {

    private static final String MASTER_KEY = "gh158-" + UUID.randomUUID();
    private static final Email MODERATOR = new Email("mark.moderator@example.com");
    private static final String ADMIN = "john.doe@example.com";
    private static final String USER = "jane.smith@example.com";

    // unikalne słowa: nie występują w data.sql ani w innych testach, więc trafienia są tylko nasze
    private static final String TITLE_WORD = "Kwazarix";
    private static final String DESCRIPTION_WORD = "Nebulowato";
    private static final long STRATEGY = 1L;
    private static final long FAMILY = 2L;

    @Container
    static final GenericContainer<?> MEILI = new GenericContainer<>(DockerImageName.parse("getmeili/meilisearch:v1.53"))
            .withEnv("MEILI_MASTER_KEY", MASTER_KEY)
            .withEnv("MEILI_ENV", "development")
            .withEnv("MEILI_NO_ANALYTICS", "true")
            .withExposedPorts(7700)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    @DynamicPropertySource
    static void meiliProperties(DynamicPropertyRegistry registry) {
        registry.add("gamehive.search.host", () -> "http://" + MEILI.getHost() + ":" + MEILI.getMappedPort(7700));
        registry.add("gamehive.search.api-key", () -> MASTER_KEY);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired SearchReindexService reindexService;
    @Autowired GameModerationService gameModerationService;
    @Autowired GameRepository gameRepository;
    @Autowired GameExpansionRepository expansionRepository;
    @Autowired PublisherRepository publisherRepository;
    @Autowired AuthorRepository authorRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired GameSearchService gameSearchService;
    @Autowired TaxonomySuggestService taxonomySuggestService;
    @Autowired @Qualifier(MeiliClientConfig.CONTENT_GATEWAY) MeiliIndexGateway contentGateway;
    @Autowired @Qualifier(MeiliClientConfig.TAXONOMY_GATEWAY) MeiliIndexGateway taxonomyGateway;
    @MockitoBean JavaMailSender mailSender;

    private final List<Long> createdGameIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // blokada reindeksu (TTL 15 min) nie może zostać po poprzednim teście
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        reindexService.reindex();
    }

    @AfterEach
    void cleanup() {
        createdGameIds.reversed().stream()
                .filter(gameRepository::existsById)
                .forEach(id -> gameModerationService.deleteGame(id, MODERATOR));
        createdGameIds.clear();
        redisTemplate.delete(SearchReindexService.REINDEX_LOCK_KEY);
    }

    // ---------- wiring (przeniesione z MeiliWiringTest) ----------

    @Test
    @DisplayName("z włączoną wyszukiwarką aktywne są implementacje Meili; dwa gatewaye na dwóch RÓŻNYCH indeksach")
    void searchEnabled_meiliImplementationsAndDistinctIndexes() {
        assertThat(gameSearchService).isInstanceOf(MeiliGameSearchService.class);
        assertThat(taxonomySuggestService).isInstanceOf(MeiliTaxonomySuggestService.class);
        assertThat(contentGateway.indexUid()).isEqualTo("gamehive_content");
        assertThat(taxonomyGateway.indexUid()).isEqualTo("gamehive_taxonomy");
        assertThat(contentGateway).isNotSameAs(taxonomyGateway);
    }

    // ---------- gamehive_content ----------

    @Test
    @DisplayName("POST /admin/search/reindex -> liczniki zgodne z bazą (treść: APPROVED, taksonomia: każdy status)")
    void reindex_countsMatchDatabase() throws Exception {
        long approvedGames = gameRepository.findAll().stream()
                .filter(game -> game.getModerationStatus() == ModerationStatus.APPROVED).count();
        long approvedExpansions = expansionRepository.findAll().stream()
                .filter(expansion -> expansion.getModerationStatus() == ModerationStatus.APPROVED).count();

        mockMvc.perform(authorized(post("/api/v1/admin/search/reindex"), ADMIN, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games").value(approvedGames))
                .andExpect(jsonPath("$.expansions").value(approvedExpansions))
                .andExpect(jsonPath("$.publishers").value(publisherRepository.count()))
                .andExpect(jsonPath("$.authors").value(authorRepository.count()));
        assertThat(approvedGames).as("data.sql ma zatwierdzone gry — inaczej test nic nie mówi").isPositive();
    }

    @Test
    @DisplayName("approve gry -> trafienie po tytule i po fragmencie opisu; delete -> dokument znika z indeksu")
    void approveThenDelete_documentAppearsAndDisappears() throws Exception {
        assertThat(searchGameIds(TITLE_WORD, "")).as("przed approve gry nie ma w indeksie").isEmpty();

        Long gameId = approvedGame(TITLE_WORD + " Prime", STRATEGY, 1, 4);

        assertThat(searchGameIds(TITLE_WORD, "")).containsExactly(gameId);
        assertThat(searchGameIds(DESCRIPTION_WORD, "")).as("description jest searchable").containsExactly(gameId);

        gameModerationService.deleteGame(gameId, MODERATOR);

        assertThat(searchGameIds(TITLE_WORD, "")).as("REMOVE po hard-delete").isEmpty();
    }

    @Test
    @DisplayName("filtr categoryId i players zawęża wyniki — gra spoza filtra NIE wraca")
    void filters_narrowResults() throws Exception {
        Long strategyDuo = approvedGame(TITLE_WORD + " Duo", STRATEGY, 1, 2);
        Long familyParty = approvedGame(TITLE_WORD + " Party", FAMILY, 3, 5);

        assertThat(searchGameIds(TITLE_WORD, ""))
                .as("bez filtra obie").containsExactlyInAnyOrder(strategyDuo, familyParty);
        assertThat(searchGameIds(TITLE_WORD, "&categoryId=" + STRATEGY)).containsExactly(strategyDuo);
        assertThat(searchGameIds(TITLE_WORD, "&categoryId=" + FAMILY)).containsExactly(familyParty);
        assertThat(searchGameIds(TITLE_WORD, "&players=4")).containsExactly(familyParty);
        assertThat(searchGameIds(TITLE_WORD, "&players=2&categoryId=" + FAMILY)).isEmpty();
    }

    // ---------- gamehive_taxonomy ----------

    @Test
    @DisplayName("/publishers/suggest na Meili -> wpisy PENDING i APPROVED")
    void suggestPublishers_returnsPendingAndApproved() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/taxonomy/publishers/suggest?q=games&limit=10"), USER, "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Pending Games')].status").value("PENDING"))
                .andExpect(jsonPath("$[?(@.name == 'Rio Grande Games')].status").value("APPROVED"));
    }

    @Test
    @DisplayName("/publishers/suggest z literówką -> trafienie (tolerancja literówek = Meili, fallback LIKE by nie znalazł)")
    void suggestPublishers_typoTolerance() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/taxonomy/publishers/suggest?q=Pendng"), USER, "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.hasItem("Pending Games")));
    }

    // ---------- pomocnicze ----------

    private List<Long> searchGameIds(String q, String extraParams) throws Exception {
        String body = mockMvc.perform(authorized(get("/api/v1/games/search?size=50&q=" + q + extraParams), USER, "ROLE_USER"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.content[*].game.id");
        return ids.stream().map(Number::longValue).toList();
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String email, String role) {
        String token = jwtService.generateToken(email, JwtTokenType.ACCESS, Set.of(role));
        return request.header("Authorization", "Bearer " + token);
    }

    /** Gra zapisana jako PENDING i zatwierdzona przez serwis — dokument trafia do indeksu ścieżką zdarzenia. */
    private Long approvedGame(String title, long categoryId, int minPlayers, int maxPlayers) {
        Game game = Game.builder()
                .title(title).description("Gra testowa GH-158, opis " + DESCRIPTION_WORD + ".")
                .submittedBy(SeededUsers.JANE_ID)
                .moderationStatus(ModerationStatus.PENDING)
                .minPlayers(minPlayers).maxPlayers(maxPlayers).playingTimeMinutes(30)
                .yearPublished(2024).minAge(8).coverImageUrl(null)
                .build();
        game.addCategory(categoryRepository.findById(categoryId).orElseThrow());
        Long id = gameRepository.saveAndFlush(game).getId();
        createdGameIds.add(id);

        gameModerationService.approve(id, MODERATOR);
        return id;
    }
}
