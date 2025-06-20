-- roles
INSERT INTO user_role (name, description)
VALUES
    ('ADMIN', 'Administrator of the system'),
    ('USER', 'Regular appUser of the system'),
    ('MODERATOR', 'Moderator with special permissions');

-- appUser details
INSERT INTO user_profiles (first_name, last_name, address, phone_number, date_of_birth, profile_picture_url)
VALUES
    ('John', 'Doe', '123 Main St', '123456789', '1990-05-15', 'https://example.com/johndoe.jpg'),
    ('Jane', 'Smith', '456 Elm St', '987654321', '1985-07-20', 'https://example.com/janesmith.jpg');

-- users
INSERT INTO application_users (username, password, email, user_profile_id, enabled)
VALUES
    ('john_doe', '{bcrypt}$2a$10$DoYAljAFrC9hTtN9zWcChu/1vxOrZiUmpU/ShCmYsHGubNwn8I/Cq', 'john.doe@example.com', 1, true),
    ('jane_smith', '{bcrypt}$2a$10$wnJKcfT8rFhyhno51MBqHeZS.ZYKXUavokV3EAQkq/WTd5E17V9fe', 'jane.smith@example.com', 2, true);

INSERT INTO user_roles (user_id, role_id)
VALUES
    (1, 1),  -- John Doe as ADMIN
    (1, 2),  -- John Doe as USER
    (2, 2);  -- Jane Smith as USER
