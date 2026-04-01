INSERT INTO user_role (name, description)
VALUES
    ('ROLE_ADMIN', 'Administrator of the system'),
    ('ROLE_USER', 'Regular appUser of the system'),
    ('ROLE_MODERATOR', 'Moderator with special permissions')
ON CONFLICT DO NOTHING;