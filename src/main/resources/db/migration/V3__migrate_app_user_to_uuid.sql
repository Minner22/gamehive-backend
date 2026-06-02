-- GH-71: app_user.id BIGINT -> uuid (UUID v7 generowany przez Hibernate).
-- Migracja DESTRUKCYJNA (drop & recreate) — patrz nota w README.
-- Dozwolona tylko dlatego, że nie ma danych produkcyjnych (środowiska dev/test, dane odtwarzalne).

ALTER TABLE user_roles DROP CONSTRAINT fk_user_roles_user;

TRUNCATE TABLE user_roles;
TRUNCATE TABLE application_users;

ALTER TABLE application_users DROP COLUMN id;
ALTER TABLE application_users ADD COLUMN id uuid PRIMARY KEY;  -- bez DEFAULT: ID generuje Hibernate

ALTER TABLE user_roles ALTER COLUMN user_id TYPE uuid USING NULL;  -- tabela pusta po TRUNCATE

ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES application_users(id) ON DELETE CASCADE;

-- Re-seed: profil dev odtwarza DevDataInitializer (generuje UUID-y); brak seedu w samej migracji.
