--liquibase formatted sql
CREATE TABLE user_role (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255)
);