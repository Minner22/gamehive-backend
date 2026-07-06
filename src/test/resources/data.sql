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
INSERT INTO categories (name) VALUES
                                  ('Strategy'), ('Family'), ('Party'), ('Cooperative');

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



