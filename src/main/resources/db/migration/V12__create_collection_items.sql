-- GH-121 (G8): prywatna kolekcja użytkownika. Dwie osobne tabele zamiast polimorficznego celu
-- (decyzja roadmapy) — prostsze FK i unikalność.
-- user_id bez FK do application_users (konwencja jak submitted_by w V8/V11 i user_audit_log w V4);
-- wpisy po skasowanym koncie sprząta listener na UserDeletedEvent.
-- game_id/expansion_id Z ON DELETE CASCADE — hard-delete celu (#119/#120) ma zabrać wpisy kolekcji.
-- Ta sama kaskada jest zadeklarowana na encji (@OnDelete), więc H2 w testach zachowuje się tak samo.
CREATE TABLE IF NOT EXISTS game_collection_items (
                                                     id BIGSERIAL PRIMARY KEY,
                                                     created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                                     updated_at TIMESTAMP WITH TIME ZONE,
                                                     user_id uuid NOT NULL,
                                                     game_id BIGINT NOT NULL REFERENCES games (id) ON DELETE CASCADE,
                                                     ownership_status VARCHAR(20) NOT NULL,
                                                     CONSTRAINT uq_game_collection_user_game UNIQUE (user_id, game_id)
);

-- Każde zapytanie listujące filtruje po user_id. Unikat (user_id, game_id) pokrywa ten lookup prefiksem,
-- ale indeks zostaje jawnie — unikat może się w przyszłości zmienić wraz z rozszerzeniem ownership_status.
CREATE INDEX IF NOT EXISTS idx_game_collection_items_user_id ON game_collection_items (user_id);

CREATE TABLE IF NOT EXISTS expansion_collection_items (
                                                          id BIGSERIAL PRIMARY KEY,
                                                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                                          updated_at TIMESTAMP WITH TIME ZONE,
                                                          user_id uuid NOT NULL,
                                                          expansion_id BIGINT NOT NULL REFERENCES game_expansions (id) ON DELETE CASCADE,
                                                          ownership_status VARCHAR(20) NOT NULL,
                                                          CONSTRAINT uq_expansion_collection_user_expansion UNIQUE (user_id, expansion_id)
);

CREATE INDEX IF NOT EXISTS idx_expansion_collection_items_user_id ON expansion_collection_items (user_id);
