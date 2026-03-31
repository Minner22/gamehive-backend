-- roles
INSERT INTO user_role (name, description)
VALUES
    ('ROLE_ADMIN', 'Administrator of the system'),
    ('ROLE_USER', 'Regular appUser of the system'),
    ('ROLE_MODERATOR', 'Moderator with special permissions')
ON CONFLICT DO NOTHING;

-- appUser details
INSERT INTO user_profiles (first_name, last_name, address, phone_number, date_of_birth, profile_picture_url)
VALUES
    ('John', 'Doe', '123 Main St', '123456789', '1990-05-15', 'https://example.com/johndoe.jpg'),
    ('Jane', 'Smith', '456 Elm St', '987654321', '1985-07-20', 'https://example.com/janesmith.jpg')
ON CONFLICT DO NOTHING;

-- users
INSERT INTO application_users (username, password, email, user_profile_id, enabled)
VALUES
    ('john_doe', '{bcrypt}$2a$10$DoYAljAFrC9hTtN9zWcChu/1vxOrZiUmpU/ShCmYsHGubNwn8I/Cq', 'john.doe@example.com', 1, true),
    ('jane_smith', '{bcrypt}$2a$10$wnJKcfT8rFhyhno51MBqHeZS.ZYKXUavokV3EAQkq/WTd5E17V9fe', 'jane.smith@example.com', 2, true)
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
VALUES
    (1, 1),  -- John Doe as ADMIN
    (1, 2),  -- John Doe as USER
    (2, 2)   -- Jane Smith as USER
ON CONFLICT DO NOTHING;