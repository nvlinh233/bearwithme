-- FR-A1: the single v1 account. Username and password are Flyway placeholders filled from
-- environment variables; the password is hashed here with BCrypt (cost 10, $2a$ format),
-- which Spring Security's BCryptPasswordEncoder accepts. pgcrypto is a trusted extension (PG 13+).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (username, password_hash)
VALUES ('${seed_username}', crypt('${seed_password}', gen_salt('bf', 10)));
