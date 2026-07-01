-- GH-114 (G1): seed kuratorowanych słowników.
-- Wydawcy i autorzy NIE są seedowani — powstają w locie (#117).
INSERT INTO categories (name) VALUES
                                  ('Strategy'), ('Family'), ('Party'), ('Abstract'), ('Cooperative'), ('Deck-building')
ON CONFLICT (name) DO NOTHING;

INSERT INTO mechanics (name) VALUES
                                 ('Worker Placement'), ('Deck-building'), ('Area Control'), ('Dice Rolling'), ('Hand Management')
ON CONFLICT (name) DO NOTHING;
