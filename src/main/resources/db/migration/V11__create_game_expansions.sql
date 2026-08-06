-- GH-120 (G7): dodatki do gier + tabele łączące dla WŁASNYCH (nadpisujących) kategorii i mechanik.
-- submitted_by/reviewed_by bez FK do application_users — zgłoszenie ma przetrwać DELETE użytkownika (jak V4/V8).
-- Kolumny nadpisań (min_players, max_players, playing_time_minutes, min_age) są NULLABLE:
-- NULL = „dziedziczę wartość z gry bazowej".
CREATE TABLE IF NOT EXISTS game_expansions (
                                               id BIGSERIAL PRIMARY KEY,
                                               created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                               updated_at TIMESTAMP WITH TIME ZONE,
                                               base_game_id BIGINT NOT NULL REFERENCES games (id),
                                               name VARCHAR(255) NOT NULL,
                                               description TEXT NOT NULL,
                                               min_players INT,
                                               max_players INT,
                                               playing_time_minutes INT,
                                               min_age INT,
                                               moderation_status VARCHAR(20) NOT NULL,
                                               submitted_by uuid NOT NULL,
                                               reviewed_by uuid,
                                               reviewed_at TIMESTAMP WITH TIME ZONE,
                                               rejection_reason TEXT,
                                               resubmission_count INT DEFAULT 0 NOT NULL
);

-- base_game_id celowo BEZ ON DELETE CASCADE: hard-delete gry z dodatkami jest blokowany w serwisie
-- (GAME_HAS_EXPANSIONS, 409); RESTRICT po stronie bazy to druga linia obrony — jak przy guardach *_IN_USE.
CREATE INDEX IF NOT EXISTS idx_game_expansions_base_game_id ON game_expansions (base_game_id);
CREATE INDEX IF NOT EXISTS idx_game_expansions_moderation_status ON game_expansions (moderation_status);
CREATE INDEX IF NOT EXISTS idx_game_expansions_submitted_by ON game_expansions (submitted_by);

-- Tabele łączące: kasowanie dodatku sprząta wpisy (ON DELETE CASCADE po stronie expansion_id).
-- Po stronie słownika brak kaskady — kategoria/mechanika używana przez dodatek nie może zniknąć po cichu.
CREATE TABLE IF NOT EXISTS expansion_category (
                                                  expansion_id BIGINT NOT NULL REFERENCES game_expansions (id) ON DELETE CASCADE,
                                                  category_id BIGINT NOT NULL REFERENCES categories (id),
                                                  PRIMARY KEY (expansion_id, category_id)
);

CREATE TABLE IF NOT EXISTS expansion_mechanic (
                                                  expansion_id BIGINT NOT NULL REFERENCES game_expansions (id) ON DELETE CASCADE,
                                                  mechanic_id BIGINT NOT NULL REFERENCES mechanics (id),
                                                  PRIMARY KEY (expansion_id, mechanic_id)
);

-- Indeksy po stronie słownika (PK złożony pokrywa tylko lookupy od strony expansion_id).
-- Bez nich check RESTRICT przy DELETE słownika i guard *_IN_USE robią sequential scan tabeli łączącej.
CREATE INDEX IF NOT EXISTS idx_expansion_category_category_id ON expansion_category (category_id);
CREATE INDEX IF NOT EXISTS idx_expansion_mechanic_mechanic_id ON expansion_mechanic (mechanic_id);
