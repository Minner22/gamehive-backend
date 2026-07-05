-- GH-116 (G3): tabela gier + tabele łączące ze słownikami (#114).
-- submitted_by/reviewed_by bez FK do application_users — zgłoszenie ma przetrwać DELETE użytkownika (jak w V4).
CREATE TABLE IF NOT EXISTS games (
                                     id BIGSERIAL PRIMARY KEY,
                                     created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                     updated_at TIMESTAMP WITH TIME ZONE,
                                     title VARCHAR(255) NOT NULL,
                                     description TEXT NOT NULL,
                                     min_players INT NOT NULL,
                                     max_players INT NOT NULL,
                                     playing_time_minutes INT NOT NULL,
                                     year_published INT NOT NULL,
                                     min_age INT NOT NULL,
                                     cover_image_url VARCHAR(512),
                                     moderation_status VARCHAR(20) NOT NULL,
                                     submitted_by uuid NOT NULL,
                                     reviewed_by uuid,
                                     reviewed_at TIMESTAMP WITH TIME ZONE,
                                     rejection_reason TEXT,
                                     resubmission_count INT DEFAULT 0 NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_games_moderation_status ON games (moderation_status);
CREATE INDEX IF NOT EXISTS idx_games_submitted_by ON games (submitted_by);

-- Tabele łączące: kasowanie gry sprząta wpisy (ON DELETE CASCADE po stronie game_id).
-- Po stronie słownika brak kaskady — słownik używany przez grę nie może zniknąć po cichu.
CREATE TABLE IF NOT EXISTS game_publisher (
                                              game_id BIGINT NOT NULL REFERENCES games (id) ON DELETE CASCADE,
                                              publisher_id BIGINT NOT NULL REFERENCES publishers (id),
                                              PRIMARY KEY (game_id, publisher_id)
);

CREATE TABLE IF NOT EXISTS game_category (
                                             game_id BIGINT NOT NULL REFERENCES games (id) ON DELETE CASCADE,
                                             category_id BIGINT NOT NULL REFERENCES categories (id),
                                             PRIMARY KEY (game_id, category_id)
);

CREATE TABLE IF NOT EXISTS game_mechanic (
                                             game_id BIGINT NOT NULL REFERENCES games (id) ON DELETE CASCADE,
                                             mechanic_id BIGINT NOT NULL REFERENCES mechanics (id),
                                             PRIMARY KEY (game_id, mechanic_id)
);

CREATE TABLE IF NOT EXISTS game_author (
                                           game_id BIGINT NOT NULL REFERENCES games (id) ON DELETE CASCADE,
                                           author_id BIGINT NOT NULL REFERENCES authors (id),
                                           PRIMARY KEY (game_id, author_id)
);

-- Indeksy po stronie słownika (PK złożony pokrywa tylko lookupy od strony game_id).
-- Postgres nie indeksuje kolumn FK automatycznie: bez nich check RESTRICT przy DELETE słownika,
-- guard *_IN_USE (#117) i zapytania "gry wg słownika" robią sequential scan tabeli łączącej.
CREATE INDEX IF NOT EXISTS idx_game_publisher_publisher_id ON game_publisher (publisher_id);
CREATE INDEX IF NOT EXISTS idx_game_category_category_id ON game_category (category_id);
CREATE INDEX IF NOT EXISTS idx_game_mechanic_mechanic_id ON game_mechanic (mechanic_id);
CREATE INDEX IF NOT EXISTS idx_game_author_author_id ON game_author (author_id);