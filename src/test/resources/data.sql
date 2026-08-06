-- roles
INSERT INTO user_role (name, description)
VALUES
    ('ROLE_ADMIN', 'Administrator of the system'),
    ('ROLE_USER', 'Regular appUser of the system'),
    ('ROLE_MODERATOR', 'Moderator with special permissions');

-- profiles (Long IDENTITY – bez zmian)
INSERT INTO user_profiles (first_name, last_name, street, city, postal_code, country, phone_number, date_of_birth, profile_picture_url)
VALUES
    ('John', 'Doe', '123 Main St', 'Springfield', '00-001', 'USA', '123456789', '1990-05-15', 'https://example.com/johndoe.jpg'),
    ('Jane', 'Smith', '456 Elm St', 'Portland', '00-002', 'USA', '987654321', '1985-07-20', 'https://example.com/janesmith.jpg'),
    ('Mark', 'Moderator', '789 Oak St', 'Denver', '00-003', 'USA', '555444333', '1988-03-10', 'https://example.com/markmod.jpg');

-- users (UUID v7 podane jawnie – kolumna uuid nie ma DEFAULT; zgodne z SeededUsers)
INSERT INTO application_users (id, username, password, email, user_profile_id, enabled)
VALUES
    ('0192a1b2-0000-7000-8000-000000000001', 'john_doe',   '{bcrypt}$2a$10$DoYAljAFrC9hTtN9zWcChu/1vxOrZiUmpU/ShCmYsHGubNwn8I/Cq', 'john.doe@example.com',   1, true),
    ('0192a1b2-0000-7000-8000-000000000002', 'jane_smith', '{bcrypt}$2a$10$wnJKcfT8rFhyhno51MBqHeZS.ZYKXUavokV3EAQkq/WTd5E17V9fe', 'jane.smith@example.com', 2, true),
    ('0192a1b2-0000-7000-8000-000000000003', 'mark_mod',   '{bcrypt}$2a$10$wnJKcfT8rFhyhno51MBqHeZS.ZYKXUavokV3EAQkq/WTd5E17V9fe', 'mark.moderator@example.com', 3, true);

INSERT INTO user_roles (user_id, role_id)
VALUES
    ('0192a1b2-0000-7000-8000-000000000001', 1),  -- John: ADMIN
    ('0192a1b2-0000-7000-8000-000000000001', 2),  -- John: USER
    ('0192a1b2-0000-7000-8000-000000000002', 2),  -- Jane: USER
    ('0192a1b2-0000-7000-8000-000000000003', 3);  -- Mark: MODERATOR

-- game dictionaries (GH-114 / G1)
-- kategoria 5 (GH-120) celowo NIE jest powiązana z żadną GRĄ — używa jej wyłącznie dodatek 1,
-- co daje czysty test guardu CATEGORY_IN_USE po stronie dodatków.
-- kategoria 3 (Party) musi pozostać niepowiązana z NICZYM (test deleteCategory_asAdmin_204).
INSERT INTO categories (name) VALUES
                                  ('Strategy'), ('Family'), ('Party'), ('Cooperative'), ('Expansion Only');

INSERT INTO mechanics (name) VALUES
                                 ('Worker Placement'), ('Deck-building'), ('Area Control'), ('Dice Rolling');

INSERT INTO publishers (name, status) VALUES
                                          ('Rio Grande Games', 'APPROVED'),
                                          ('Z-Man Games', 'APPROVED'),
                                          ('Pending Games', 'PENDING');

INSERT INTO authors (first_name, last_name, status) VALUES
                                                        ('Uwe', 'Rosenberg', 'APPROVED'),
                                                        ('Reiner', 'Knizia', 'APPROVED'),
                                                        ('Oczekujacy', 'Autor', 'PENDING');


-- games (GH-116 / G3) — ids 1..3 z kolejności insertów (BIGSERIAL), jak w słownikach powyżej
INSERT INTO games (title, description, min_players, max_players, playing_time_minutes, year_published, min_age, cover_image_url,
                   moderation_status, submitted_by, reviewed_by, reviewed_at, rejection_reason, resubmission_count)
VALUES
    ('Agricola', 'Klasyczna gra o rozwoju farmy.', 1, 4, 120, 2007, 12, 'https://example.com/agricola.jpg',
     'APPROVED', '0192a1b2-0000-7000-8000-000000000002', '0192a1b2-0000-7000-8000-000000000003', CURRENT_TIMESTAMP, NULL, 0),
    ('Pandemic', 'Kooperacyjna walka z epidemiami.', 2, 4, 45, 2008, 8, NULL,
     'PENDING', '0192a1b2-0000-7000-8000-000000000002', NULL, NULL, NULL, 0),
    ('Odrzucona Gra', 'Zgłoszenie odrzucone przez moderatora.', 2, 6, 30, 2020, 10, NULL,
     'REJECTED', '0192a1b2-0000-7000-8000-000000000001', '0192a1b2-0000-7000-8000-000000000003', CURRENT_TIMESTAMP, 'Duplikat istniejącej gry', 1);

-- relacje: publishers (1=Rio Grande, 2=Z-Man, 3=Pending Games), categories (1=Strategy, 2=Family, 4=Cooperative),
--          mechanics (1=Worker Placement), authors (1=Uwe Rosenberg)
INSERT INTO game_publisher (game_id, publisher_id) VALUES (1, 1), (1, 2), (2, 2), (3, 3);
INSERT INTO game_category (game_id, category_id) VALUES (1, 1), (2, 4), (3, 2);
INSERT INTO game_mechanic (game_id, mechanic_id) VALUES (1, 1);
INSERT INTO game_author (game_id, author_id) VALUES (1, 1);

-- games 4..6 (GH-117 / G4) — fixtury pod „moje zgłoszenia", edycję i resubmit
-- UWAGA: limit resubmisji w testach = 2 (application-test.yml), więc gra 6 (count = 2) jest na limicie
INSERT INTO games (title, description, min_players, max_players, playing_time_minutes, year_published, min_age, cover_image_url,
                   moderation_status, submitted_by, reviewed_by, reviewed_at, rejection_reason, resubmission_count)
VALUES
    ('Szkic Jane', 'Roboczy szkic zgłoszenia Jane.', 1, 4, 30, 2025, 8, NULL,
     'DRAFT', '0192a1b2-0000-7000-8000-000000000002', NULL, NULL, NULL, 0),
    ('Odrzucona Jane', 'Zgłoszenie Jane po pierwszym odrzuceniu.', 2, 4, 60, 2023, 10, NULL,
     'REJECTED', '0192a1b2-0000-7000-8000-000000000002', '0192a1b2-0000-7000-8000-000000000003', CURRENT_TIMESTAMP, 'Zbyt krótki opis', 1),
    ('Limit Jane', 'Zgłoszenie Jane z wyczerpanym limitem poprawek.', 2, 4, 60, 2022, 10, NULL,
     'REJECTED', '0192a1b2-0000-7000-8000-000000000002', '0192a1b2-0000-7000-8000-000000000003', CURRENT_TIMESTAMP, 'Wielokrotnie odrzucane', 2);

INSERT INTO game_publisher (game_id, publisher_id) VALUES (4, 1), (5, 1), (6, 2);
INSERT INTO game_category (game_id, category_id) VALUES (4, 2), (5, 2), (6, 1);


-- gra 7 (GH-120 / G7) — DRUGA gra APPROVED, baza dla wszystkich dodatków.
-- Dodatki nie mogą wisieć przy Agricoli (gra 1): GameModerationControllerTest.delete_approved_204 ją kasuje,
-- a hard-delete gry z dodatkami jest blokowany (GAME_HAS_EXPANSIONS).
-- Parametry dobrane tak, by NIE wpaść w żaden istniejący filtr biblioteki gier:
--   publisher 2 (nie 1, nie 3), category 2 (nie 1, nie 4), mechanic 3 (nie 1),
--   2..2 graczy (nie łapie players=3 ani players=5), rok 2000 (nie 2007, nie 1999).
-- Właściciel: John — dzięki temu asercja findBySubmittedBy(JANE_ID) zostaje bez zmian.
INSERT INTO games (title, description, min_players, max_players, playing_time_minutes, year_published, min_age, cover_image_url,
                   moderation_status, submitted_by, reviewed_by, reviewed_at, rejection_reason, resubmission_count)
VALUES
    ('Carcassonne', 'Baza dla dodatków w testach.', 2, 2, 45, 2000, 8, NULL,
     'APPROVED', '0192a1b2-0000-7000-8000-000000000001', '0192a1b2-0000-7000-8000-000000000003', CURRENT_TIMESTAMP, NULL, 0);

INSERT INTO game_publisher (game_id, publisher_id) VALUES (7, 2);
INSERT INTO game_category (game_id, category_id) VALUES (7, 2);   -- Family
INSERT INTO game_mechanic (game_id, mechanic_id) VALUES (7, 3);   -- Area Control

-- dodatki 1..6 (GH-120 / G7) — wszystkie oparte o grę 7.
-- UWAGA: dodatek 2 jest JEDYNYM PENDING dodatkiem (GameExpansionRepositoryTest robi containsExactly).
-- UWAGA: limit resubmisji w testach = 2 (application-test.yml), więc dodatek 5 stoi dokładnie na limicie.
INSERT INTO game_expansions (base_game_id, name, description, min_players, max_players, playing_time_minutes, min_age,
                             moderation_status, submitted_by, reviewed_by, reviewed_at, rejection_reason, resubmission_count)
VALUES
    -- 1: APPROVED, nadpisuje maxPlayers i kategorie; reszta dziedziczona (biblioteka + edycja/delete moderatora)
    (7, 'Carcassonne: Rzeka', 'Zatwierdzony dodatek z częściowymi nadpisaniami.', NULL, 6, NULL, NULL,
     'APPROVED', '0192a1b2-0000-7000-8000-000000000002', '0192a1b2-0000-7000-8000-000000000003', CURRENT_TIMESTAMP, NULL, 0),
    -- 2: PENDING, ZERO nadpisań — czyste dziedziczenie (kolejka moderacji + test dziedziczenia w DTO)
    (7, 'Carcassonne: Karczmy', 'Dodatek oczekujący na decyzję, bez nadpisań.', NULL, NULL, NULL, NULL,
     'PENDING', '0192a1b2-0000-7000-8000-000000000002', NULL, NULL, NULL, 0),
    -- 3: DRAFT Jane (edycja + submit)
    (7, 'Szkic Dodatku Jane', 'Roboczy szkic dodatku.', NULL, NULL, NULL, NULL,
     'DRAFT', '0192a1b2-0000-7000-8000-000000000002', NULL, NULL, NULL, 0),
    -- 4: REJECTED count=1 (resubmit)
    (7, 'Odrzucony Dodatek Jane', 'Dodatek po pierwszym odrzuceniu.', NULL, NULL, NULL, NULL,
     'REJECTED', '0192a1b2-0000-7000-8000-000000000002', '0192a1b2-0000-7000-8000-000000000003', CURRENT_TIMESTAMP, 'Za mało szczegółów', 1),
    -- 5: REJECTED count=2 = limit testowy (RESUBMISSION_LIMIT_EXCEEDED + unlock)
    (7, 'Limit Dodatku Jane', 'Dodatek z wyczerpanym limitem poprawek.', NULL, NULL, NULL, NULL,
     'REJECTED', '0192a1b2-0000-7000-8000-000000000002', '0192a1b2-0000-7000-8000-000000000003', CURRENT_TIMESTAMP, 'Wielokrotnie odrzucane', 2),
    -- 6: DRAFT Johna — „cudze zgłoszenie" z perspektywy Jane (enumeration-safe 404)
    (7, 'Szkic Dodatku Johna', 'Cudzy szkic dodatku.', NULL, NULL, NULL, NULL,
     'DRAFT', '0192a1b2-0000-7000-8000-000000000001', NULL, NULL, NULL, 0);

-- tylko dodatek 1 nadpisuje kategorie (5 = Expansion Only); nie ma własnych mechanik,
-- więc dziedziczy Area Control z gry 7 (test dziedziczenia kolekcji)
INSERT INTO expansion_category (expansion_id, category_id) VALUES (1, 5);


-- kolekcje (GH-121 / G8) — MVP zna wyłącznie ownership_status = OWNED.
-- Cele dobrane tak, by kaskada z hard-delete była pokryta również przez ISTNIEJĄCE testy kasujące
-- grę 1 (GameRepositoryTest, GameModerationControllerTest) i dodatek 1 (GameExpansionModerationControllerTest):
--   Jane ma w kolekcji grę 1 (Agricola) i dodatek 1 (Carcassonne: Rzeka),
--   John ma grę 7 (Carcassonne) — przypadek „cudzy wpis" dla testów izolacji.
-- UWAGA: Jane NIE ma gry 7, mimo że ma dodatek 1 oparty właśnie o nią — to fixture pod kryterium
--        „dodatek dodaje się do kolekcji niezależnie od gry bazowej".
-- UWAGA: Mark (moderator) musi mieć PUSTĄ kolekcję — jest użytkownikiem „bez wpisów" w testach.
-- created_at ustawiane jawnie (inaczej byłoby NULL jak w pozostałych fixture'ach) — DTO wystawia addedAt.
INSERT INTO game_collection_items (created_at, user_id, game_id, ownership_status)
VALUES
    (CURRENT_TIMESTAMP, '0192a1b2-0000-7000-8000-000000000002', 1, 'OWNED'),   -- Jane: Agricola
    (CURRENT_TIMESTAMP, '0192a1b2-0000-7000-8000-000000000001', 7, 'OWNED');   -- John: Carcassonne

INSERT INTO expansion_collection_items (created_at, user_id, expansion_id, ownership_status)
VALUES
    (CURRENT_TIMESTAMP, '0192a1b2-0000-7000-8000-000000000002', 1, 'OWNED');   -- Jane: Carcassonne: Rzeka



