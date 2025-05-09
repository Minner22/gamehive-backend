# GameHive

## TODO:

1. ***Użytkownicy i autoryzacja***
    - **Rejestracja użytkowników** – formularz rejestracyjny, walidacja danych, zapis w bazie.
    - **Logowanie** – autoryzacja użytkowników, hashowanie haseł, JWT (JSON Web Token) do zarządzania sesjami.
    - **Profil użytkownika** – możliwość przeglądania i edytowania danych profilowych.
2. ***Gry planszowe***
    - **Dodawanie gier** – funkcjonalność pozwalająca użytkownikom na dodawanie gier do własnej biblioteki.
    - **Przeglądanie gier** – wyświetlanie listy gier użytkownika oraz innych osób w grupie.
    - **Kategorie gier i mechaniki** – przypisywanie gier do kategorii, dodanie mechanik.
3. ***Grupy***
    - **Tworzenie i zarządzanie grupami** – możliwość tworzenia grup, zapraszania członków, edycja grupy.
    - **Przeglądanie gier w grupach** – podgląd gier posiadanych przez członków grupy.
4. ***Wypożyczanie gier***
    - **System wypożyczania** – mechanizm do wypożyczania gier od innych użytkowników w grupie.
    - **Śledzenie historii wypożyczeń** – informacje o datach wypożyczenia i zwrotu.
5. ***Oceny i recenzje***
    - **Dodawanie ocen i recenzji** – użytkownicy mogą oceniać gry oraz pisać recenzje.
    - **Wyświetlanie recenzji** – system wyświetlający oceny i opinie o grach.
6. ***Multiplayer i statystyki***
    - **Rejestrowanie sesji multiplayer** – zapisywanie wyników sesji multiplayer i śledzenie wyników.
    - **Statystyki gracza** – wyświetlanie statystyk na podstawie sesji.

---

## Plan działania: Użytkownicy i autoryzacja

1. ***Projektowanie bazy danych***
    - **Tabela Users** – zawiera dane użytkowników, takie jak username, email, password (hashed), role, oraz znaczniki czasowe (np. created_at).
    - **Tabela UserDetails** – szczegóły dotyczące użytkownika: first_name, last_name, address, itp.
    - **JWT (JSON Web Tokens)** – tokeny autoryzacyjne, które będą służyć do zarządzania sesjami po stronie klienta.
2. ***Rejestracja użytkownika***
    - Endpoint: POST /api/auth/register
    - Walidacja danych użytkownika (email, hasło itp.).
    - Hashowanie hasła (np. za pomocą BCrypt).
    - Sprawdzanie unikalności emaila/username.
    - Wysłanie linka aktywacyjnego.
    - Odblokowanie użytkownika po wejściu w link aktywacyjny.
    - Zapis danych w bazie.
3. ***Logowanie użytkownika***
    - Endpoint: POST /api/auth/login
    - Walidacja emaila i hasła.
    - Weryfikacja hasła w bazie (hash).
    - Generowanie tokena JWT po poprawnym zalogowaniu.
4. ***Middleware do autoryzacji***
    - Implementacja middleware do ochrony endpointów, które wymagają zalogowania użytkownika.
    - Sprawdzanie tokena JWT przy requestach.
5. ***Edycja profilu użytkownika***
    - Endpoint: PUT /api/users/{id}
    - Umożliwienie użytkownikowi aktualizacji swoich danych (np. imię, nazwisko, bio, itp.).
    - Zabezpieczenie edycji tylko dla właściciela profilu.
6. ***Resetowanie hasła (opcjonalnie)***
    - Endpoint: POST /api/auth/reset-password
    - Generowanie linku resetującego hasło, który jest wysyłany na email użytkownika.
    - Nowe hasło zapisane po weryfikacji.

### Kolejność działania:

1. **Stwórz strukturę backendu (model, repository, service) dla użytkowników.**
2. **Zaimplementuj rejestrację użytkowników** – to podstawa.
3. **Zaimplementuj logowanie i autoryzację** – JWT jako ochrona API.
4. **Dodaj middleware do ochrony zasobów.**
5. **Rozszerz możliwości na edycję profilu.**
6. **Jeśli chcesz, zajmij się resetowaniem hasła.**

## Plan działania: Gry planszowe

1. ***Projektowanie bazy danych***
    - **Tabela BoardGames** – dane o grach, takie jak title, description, publisher, category, min_players, max_players, itp.
    - **Tabela Category** – kategorie gier (np. przygodowe, strategiczne).
    - **Tabela UsersBoardGames** – łącznik między użytkownikami a ich grami.
    - **Tabela GameLoans** – system wypożyczania gier, który będzie śledził kto wypożycza daną grę, na jaki okres.
2. ***Dodawanie gry przez użytkownika***
    - Endpoint: POST /api/boardgames
    - Umożliwienie użytkownikowi dodania gry do swojej kolekcji.
    - Sprawdzanie, czy gra już istnieje w bazie.
    - Jeśli gra nie istnieje, możliwość dodania nowej pozycji do globalnej bazy gier.
3. ***Przeglądanie dostępnych gier***
    - Endpoint: GET /api/boardgames
    - Zwracanie listy dostępnych gier dla użytkowników (z możliwością filtrowania po kategorii, liczbie graczy, itp.).
    - Endpoint: GET /api/boardgames/{id} – szczegóły wybranej gry.
4. ***Kategorie gier***
    - Endpoint: GET /api/categories
    - Umożliwienie przeglądania kategorii gier oraz przypisanie gier do kategorii podczas ich dodawania.
5. ***Wypożyczanie gier***
    - Endpoint: POST /api/gameloans
    - Pozwolenie użytkownikom na wypożyczanie gier od innych członków grupy.
    - Endpoint: PUT /api/gameloans/{id} – aktualizacja statusu wypożyczenia (np. oznaczenie gry jako zwróconej).
6. ***Edycja i usuwanie gier***
    - Endpoint: PUT /api/boardgames/{id}
    - Umożliwienie użytkownikowi aktualizacji danych o grze (np. opis, liczba graczy).
    - Endpoint: DELETE /api/boardgames/{id} – usunięcie gry z kolekcji użytkownika.

### Kolejność działania:

1. **Stwórz backendową strukturę dla zarządzania grami planszowymi** - Modele, repozytoria, serwisy.
2. **Zaimplementuj dodawanie i przeglądanie gier** - Podstawowe CRUD operacje.
3. **Zaimplementuj system kategorii** - Przypisywanie gier do kategorii.
4. **Dodaj funkcjonalność wypożyczania** - Użytkownicy będą mogli wypożyczać gry od innych.
5. **Dodaj edycję i usuwanie gier.**
6. **Przejdź do implementacji frontendu** - Stwórz widoki do zarządzania grami oraz wypożyczaniem.
