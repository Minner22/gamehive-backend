--liquibase formatted sql
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES application_users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES user_role(id)
);