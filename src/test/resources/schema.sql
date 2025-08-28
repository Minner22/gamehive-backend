-- === user_profiles ===
CREATE TABLE user_profiles (
                              id BIGSERIAL PRIMARY KEY,
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                              updated_at TIMESTAMP WITH TIME ZONE,
                              first_name VARCHAR(255),
                              last_name VARCHAR(255),
                              address VARCHAR(255),
                              phone_number VARCHAR(15),
                              date_of_birth DATE,
                              profile_picture_url VARCHAR(255)
);

-- === application_users ===
CREATE TABLE application_users (
                                   id BIGSERIAL PRIMARY KEY,
                                   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                   updated_at TIMESTAMP WITH TIME ZONE,
                                   username VARCHAR(255) NOT NULL UNIQUE,
                                   password VARCHAR(255) NOT NULL,
                                   email VARCHAR(255) NOT NULL UNIQUE,
                                   user_profile_id BIGINT,
                                   enabled BOOLEAN NOT NULL,
                                   CONSTRAINT fk_user_user_details FOREIGN KEY (user_profile_id)
                                       REFERENCES user_profiles(id) ON DELETE SET NULL
);

-- === user_role ===
CREATE TABLE user_role (
                           id BIGSERIAL PRIMARY KEY,
                           created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                           updated_at TIMESTAMP WITH TIME ZONE,
                           name VARCHAR(255) NOT NULL,
                           description VARCHAR(255)
);

-- === user_roles (relacja N:M użytkownik – role) ===
CREATE TABLE user_roles (
                            user_id BIGINT NOT NULL,
                            role_id BIGINT NOT NULL,
                            PRIMARY KEY (user_id, role_id),
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES application_users(id) ON DELETE CASCADE,
                            CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES user_role(id) ON DELETE CASCADE
);

-- === user_refresh_tokens ===
CREATE TABLE user_refresh_tokens (
                            id BIGSERIAL PRIMARY KEY,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                            updated_at TIMESTAMP WITH TIME ZONE,
                            user_id BIGINT NOT NULL,
                            jti VARCHAR(255) NOT NULL UNIQUE,
                            expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                            revoked BOOLEAN NOT NULL DEFAULT FALSE,
                            CONSTRAINT fk_user_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES application_users(id) ON DELETE CASCADE
);
