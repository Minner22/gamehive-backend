--liquibase formatted sql
CREATE TABLE user_details (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    address VARCHAR(255),
    phone_number VARCHAR(15),
    date_of_birth DATE,
    profile_picture_url VARCHAR(255)
);