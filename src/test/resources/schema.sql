-- === user_profiles === (Long IDENTITY – bez zmian)
CREATE TABLE user_profiles (
                               id BIGSERIAL PRIMARY KEY,
                               created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                               updated_at TIMESTAMP WITH TIME ZONE,
                               first_name VARCHAR(255),
                               last_name VARCHAR(255),
                               street VARCHAR(255),
                               city VARCHAR(255),
                               postal_code VARCHAR(20),
                               country VARCHAR(100),
                               phone_number VARCHAR(15),
                               date_of_birth DATE,
                               profile_picture_url VARCHAR(255)
);

-- === application_users === (PK uuid; user_profile_id nadal BIGINT)
CREATE TABLE application_users (
                                   id uuid PRIMARY KEY,
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

-- === user_role === (Long IDENTITY – bez zmian)
CREATE TABLE user_role (
                           id BIGSERIAL PRIMARY KEY,
                           created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                           updated_at TIMESTAMP WITH TIME ZONE,
                           name VARCHAR(255) NOT NULL,
                           description VARCHAR(255)
);

-- === user_roles (N:M) === (user_id uuid -> application_users.id)
CREATE TABLE user_roles (
                            user_id uuid NOT NULL,
                            role_id BIGINT NOT NULL,
                            PRIMARY KEY (user_id, role_id),
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES application_users(id) ON DELETE CASCADE,
                            CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES user_role(id) ON DELETE CASCADE
);

--gry
CREATE TABLE IF NOT EXISTS publishers (
                                          id BIGSERIAL PRIMARY KEY,
                                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                          updated_at TIMESTAMP WITH TIME ZONE,
                                          name VARCHAR(255) NOT NULL UNIQUE,
                                          status VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS categories (
                                          id BIGSERIAL PRIMARY KEY,
                                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                          updated_at TIMESTAMP WITH TIME ZONE,
                                          name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS mechanics (
                                         id BIGSERIAL PRIMARY KEY,
                                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                         updated_at TIMESTAMP WITH TIME ZONE,
                                         name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS authors (
                                       id BIGSERIAL PRIMARY KEY,
                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                       updated_at TIMESTAMP WITH TIME ZONE,
                                       first_name VARCHAR(255) NOT NULL,
                                       last_name VARCHAR(255) NOT NULL,
                                       CONSTRAINT uq_authors_name UNIQUE (first_name, last_name)
);

