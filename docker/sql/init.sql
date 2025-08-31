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

CREATE TABLE IF NOT EXISTS "refresh_token" (
    id BIGSERIAL PRIMARY KEY,
    refresh_token VARCHAR(500) UNIQUE NOT NULL,
    username VARCHAR(255) NOT NULL,
    expiration_date TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ip_address VARCHAR(45),
    device_name VARCHAR(255),
    os_name VARCHAR(255),
    browser_name VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_username FOREIGN KEY (username) REFERENCES "user"(username) ON DELETE CASCADE
);