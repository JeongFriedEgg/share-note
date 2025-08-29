CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    authorities VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP WITHOUT TIME ZONE
);

--INSERT INTO "user" (username, password, roles)
--VALUES ('testuser', '$2a$10$FDp2CJ7TypXuD7OqZdByrOhLLz.xSoJDNYVUUeymNTzTER/dP4k8y', 'ROLE_USER');