--liquibase formatted sql
CREATE TABLE application_users (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    user_details_id BIGINT,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT fk_user_user_details FOREIGN KEY (user_details_id) REFERENCES user_details(id)
);
