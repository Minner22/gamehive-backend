-- roles
INSERT INTO user_role (name, description)
VALUES
    ('ADMIN', 'Administrator of the system'),
    ('USER', 'Regular user of the system'),
    ('MODERATOR', 'Moderator with special permissions');

-- user details
INSERT INTO user_details (first_name, last_name, address, phone_number, date_of_birth, profile_picture_url)
VALUES
    ('John', 'Doe', '123 Main St', '123456789', '1990-05-15', 'https://example.com/johndoe.jpg'),
    ('Jane', 'Smith', '456 Elm St', '987654321', '1985-07-20', 'https://example.com/janesmith.jpg');

-- users
INSERT INTO application_users (username, password, email, user_details_id, enabled)
VALUES
    ('john_doe', 'password123', 'john.doe@example.com', 1, true),
    ('jane_smith', 'password456', 'jane.smith@example.com', 2, true);

INSERT INTO user_roles (user_id, role_id)
VALUES
    (1, 1),  -- John Doe as ADMIN
    (1, 2),  -- John Doe as USER
    (2, 2);  -- Jane Smith as USER
