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
    ('Jane', 'Smith', '456 Elm St', 'Portland', '00-002', 'USA', '987654321', '1985-07-20', 'https://example.com/janesmith.jpg');

-- users (UUID v7 podane jawnie – kolumna uuid nie ma DEFAULT; zgodne z SeededUsers)
INSERT INTO application_users (id, username, password, email, user_profile_id, enabled)
VALUES
    ('0192a1b2-0000-7000-8000-000000000001', 'john_doe',   '{bcrypt}$2a$10$DoYAljAFrC9hTtN9zWcChu/1vxOrZiUmpU/ShCmYsHGubNwn8I/Cq', 'john.doe@example.com',   1, true),
    ('0192a1b2-0000-7000-8000-000000000002', 'jane_smith', '{bcrypt}$2a$10$wnJKcfT8rFhyhno51MBqHeZS.ZYKXUavokV3EAQkq/WTd5E17V9fe', 'jane.smith@example.com', 2, true);

INSERT INTO user_roles (user_id, role_id)
VALUES
    ('0192a1b2-0000-7000-8000-000000000001', 1),  -- John: ADMIN
    ('0192a1b2-0000-7000-8000-000000000001', 2),  -- John: USER
    ('0192a1b2-0000-7000-8000-000000000002', 2);  -- Jane: USER

-- game dictionaries (GH-114 / G1)
INSERT INTO categories (name) VALUES
                                  ('Strategy'), ('Family'), ('Party'), ('Cooperative');

INSERT INTO mechanics (name) VALUES
                                 ('Worker Placement'), ('Deck-building'), ('Area Control'), ('Dice Rolling');

INSERT INTO publishers (name, status) VALUES
                                          ('Rio Grande Games', 'APPROVED'),
                                          ('Z-Man Games', 'APPROVED');

INSERT INTO authors (first_name, last_name) VALUES
                                                ('Uwe', 'Rosenberg'),
                                                ('Reiner', 'Knizia');
