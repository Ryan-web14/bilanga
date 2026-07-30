-- ============================================================
-- V1 — Socle SÉCURITÉ, AUTH, AUDIT, IDEMPOTENCY
-- Aligné exactement sur les entités JPA (ddl-auto: validate).
-- Convention d'ID :
--   * @IdGeneration (Snowflake, fourni par l'app)  -> BIGINT
--   * @GeneratedValue(IDENTITY)                     -> BIGSERIAL
-- ============================================================

-- Extension pour la recherche floue (trigram) utilisée par
-- les fonctions search_admin_* plus bas.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- USERS  (@IdGeneration -> BIGINT ; soft-delete via 'deleted')
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
                                     id                    BIGINT PRIMARY KEY,
                                     user_id               VARCHAR(255) NOT NULL UNIQUE,
                                     email                 VARCHAR(255) NOT NULL UNIQUE,
                                     firstname             VARCHAR(255),
                                     lastname              VARCHAR(255),
                                     password_hash         VARCHAR(255) NOT NULL,
                                     last_login            TIMESTAMP,
                                     created_at            TIMESTAMP NOT NULL,
                                     updated_at            TIMESTAMP,
                                     deleted               BOOLEAN DEFAULT FALSE,
                                     is_account_expired    BOOLEAN,
                                     is_account_locked     BOOLEAN,
                                     is_account_enabled    BOOLEAN,
                                     failed_login_attempts INTEGER
);

-- ============================================================
-- ROLE  (@GeneratedValue IDENTITY -> BIGSERIAL)
-- ============================================================
CREATE TABLE IF NOT EXISTS role (
                                    id             BIGSERIAL PRIMARY KEY,
                                    name           VARCHAR(250) NOT NULL UNIQUE,
                                    display_name   VARCHAR(255),
                                    description    VARCHAR(255),
                                    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
                                    version        INTEGER DEFAULT 0,
                                    created_at     TIMESTAMP,
                                    updated_at     TIMESTAMP,
                                    is_active      BOOLEAN NOT NULL DEFAULT TRUE
);

-- ============================================================
-- PERMISSION  (@GeneratedValue IDENTITY -> BIGSERIAL)
-- ============================================================
CREATE TABLE IF NOT EXISTS permission (
                                          id                   BIGSERIAL PRIMARY KEY,
                                          name                 VARCHAR(100) NOT NULL,
                                          display_name         VARCHAR(100),
                                          module               VARCHAR(50) NOT NULL,
                                          action               VARCHAR(60) NOT NULL,
                                          is_system_permission BOOLEAN NOT NULL DEFAULT FALSE,
                                          is_active            BOOLEAN NOT NULL DEFAULT TRUE
);

-- ============================================================
-- ROLE_PERMISSION  (clé composite role_id + permission_id)
-- ============================================================
CREATE TABLE IF NOT EXISTS role_permission (
                                               role_id       BIGINT NOT NULL,
                                               permission_id BIGINT NOT NULL,
                                               created_by    VARCHAR(255) NOT NULL,
                                               is_active     BOOLEAN NOT NULL DEFAULT TRUE,
                                               PRIMARY KEY (role_id, permission_id),
                                               CONSTRAINT role_fk       FOREIGN KEY (role_id)       REFERENCES role(id),
                                               CONSTRAINT permission_fk FOREIGN KEY (permission_id) REFERENCES permission(id)
);

-- ============================================================
-- ROLE_USER  (clé composite role_id + user_id)
-- ============================================================
CREATE TABLE IF NOT EXISTS role_user (
                                         role_id     BIGINT NOT NULL,
                                         user_id     BIGINT NOT NULL,
                                         assigned_by VARCHAR(255) NOT NULL,
                                         assigned_at TIMESTAMP NOT NULL,
                                         updated_at  TIMESTAMP,
                                         PRIMARY KEY (role_id, user_id),
                                         CONSTRAINT fk_role_user_role FOREIGN KEY (role_id) REFERENCES role(id),
                                         CONSTRAINT fk_role_user_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ============================================================
-- REFRESH_TOKEN  (@IdGeneration -> BIGINT)
-- ============================================================
CREATE TABLE IF NOT EXISTS refresh_token (
                                             id         BIGINT PRIMARY KEY,
                                             token      VARCHAR(255),
                                             user_id    BIGINT NOT NULL,
                                             expiration TIMESTAMP,
                                             revoked    BOOLEAN,
                                             CONSTRAINT fk_user_token FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ============================================================
-- PASSWORD_RESET_TOKEN  (@IdGeneration -> BIGINT)
-- ============================================================
CREATE TABLE IF NOT EXISTS password_reset_token (
                                                    id             BIGINT PRIMARY KEY,
                                                    password_token VARCHAR(255),
                                                    user_id        BIGINT NOT NULL,
                                                    expiry_date    TIMESTAMP NOT NULL,
                                                    used           BOOLEAN NOT NULL,
                                                    created_at     TIMESTAMP NOT NULL,
                                                    expiration     BIGINT,
                                                    created_by     VARCHAR(255),
                                                    CONSTRAINT fk_pwd_reset_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ============================================================
-- ONE_TIME_TOKEN  (@IdGeneration -> BIGINT)
-- ============================================================
CREATE TABLE IF NOT EXISTS one_time_token (
                                              id         BIGINT PRIMARY KEY,
                                              token      VARCHAR(255),
                                              user_id    BIGINT NOT NULL,
                                              is_used    BOOLEAN,
                                              created_at TIMESTAMP,
                                              expired_at TIMESTAMP,
                                              expiration BIGINT,
                                              CONSTRAINT one_time_token_user_fk FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ============================================================
-- AUDIT_LOG  (@IdGeneration -> BIGINT ; jsonb metadata/diff)
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_log (
                                         id            BIGINT PRIMARY KEY,
                                         created_at    TIMESTAMP NOT NULL,
                                         actor_id      BIGINT NOT NULL,
                                         actor_email   VARCHAR(255) NOT NULL,
                                         action        VARCHAR(255),
                                         ressource     VARCHAR(255),
                                         audit_status  VARCHAR(20),
                                         module        VARCHAR(255) NOT NULL,
                                         ip_address    VARCHAR(255),
                                         user_agent    VARCHAR(255),
                                         session_id    VARCHAR(255),
                                         error_code    VARCHAR(255),
                                         error_message TEXT,
                                         metadata_json JSONB,
                                         diff_json     JSONB
);

-- ============================================================
-- USER_SESSIONS  (@IdGeneration -> BIGINT ; session_id UUID)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_sessions (
                                             id             BIGINT PRIMARY KEY,
                                             session_id     UUID NOT NULL UNIQUE,
                                             user_id        BIGINT NOT NULL,
                                             refresh_token  VARCHAR(255),
                                             created_at     TIMESTAMP NOT NULL,
                                             expired_at     TIMESTAMP NOT NULL,
                                             last_seen      TIMESTAMP NOT NULL,
                                             expires_in     BIGINT NOT NULL,
                                             ip_address     VARCHAR(255),
                                             user_agent     VARCHAR(255),
                                             device_type    VARCHAR(255),
                                             is_active      BOOLEAN NOT NULL,
                                             revoked        BOOLEAN NOT NULL,
                                             role_snapshot  VARCHAR(255),
                                             location_guess VARCHAR(255),
                                             CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ============================================================
-- SETTING_AUDIT_LOGS  (@IdGeneration -> BIGINT)
-- ============================================================
CREATE TABLE IF NOT EXISTS setting_audit_logs (
                                                  id          BIGINT PRIMARY KEY,
                                                  setting_key VARCHAR(255),
                                                  old_value   TEXT,
                                                  new_value   TEXT,
                                                  changed_by  BIGINT,
                                                  changed_at  TIMESTAMP,
                                                  reason      VARCHAR(255)
);

-- ============================================================
-- IDEMPOTENCY_RECORD  (@IdGeneration -> BIGINT)
-- ============================================================
CREATE TABLE IF NOT EXISTS idempotency_record (
                                                  id             BIGINT PRIMARY KEY,
                                                  operation      VARCHAR(150) NOT NULL,
                                                  idempotency_key VARCHAR(200) NOT NULL,
                                                  request_hash   VARCHAR(128) NOT NULL,
                                                  status         VARCHAR(32) NOT NULL,
                                                  response_body  TEXT,
                                                  error_body     TEXT,
                                                  completed_at   TIMESTAMP,
                                                  created_at     TIMESTAMP NOT NULL,
                                                  updated_at     TIMESTAMP NOT NULL,
                                                  CONSTRAINT uq_idempotency UNIQUE (operation, idempotency_key)
);

-- ============================================================
-- INDEX trigram pour la recherche floue (pg_trgm)
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_users_email_trgm     ON users     USING gin (email gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_firstname_trgm ON users     USING gin (firstname gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_lastname_trgm  ON users     USING gin (lastname gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_role_name_trgm       ON role      USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_perm_name_trgm       ON permission USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_perm_module_trgm     ON permission USING gin (module gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_perm_action_trgm     ON permission USING gin (action gin_trgm_ops);

-- ============================================================
-- FONCTIONS DE RECHERCHE FLOUE (pg_trgm)
-- Chacune renvoie (id, score) où score = similarité trigram.
-- Utilisées par les repositories via :
--   JOIN search_admin_xxx(:query) s ON s.id = t.id
-- ============================================================

-- Recherche d'utilisateurs (email, prénom, nom)
CREATE OR REPLACE FUNCTION search_admin_user(query TEXT)
    RETURNS TABLE (id BIGINT, score REAL) AS $$
SELECT u.id,
       GREATEST(
               similarity(coalesce(u.email, ''),     query),
               similarity(coalesce(u.firstname, ''), query),
               similarity(coalesce(u.lastname, ''),  query)
       ) AS score
FROM users u
WHERE u.deleted = FALSE
  AND (
    u.email     ILIKE '%' || query || '%'
        OR u.firstname ILIKE '%' || query || '%'
        OR u.lastname  ILIKE '%' || query || '%'
        OR similarity(coalesce(u.email, ''),     query) > 0.2
        OR similarity(coalesce(u.firstname, ''), query) > 0.2
        OR similarity(coalesce(u.lastname, ''),  query) > 0.2
    );
$$ LANGUAGE sql STABLE;

-- Recherche de rôles (name, display_name, description)
CREATE OR REPLACE FUNCTION search_admin_role(query TEXT)
    RETURNS TABLE (id BIGINT, score REAL) AS $$
SELECT r.id,
       GREATEST(
               similarity(coalesce(r.name, ''),         query),
               similarity(coalesce(r.display_name, ''), query),
               similarity(coalesce(r.description, ''),  query)
       ) AS score
FROM role r
WHERE r.name         ILIKE '%' || query || '%'
   OR r.display_name ILIKE '%' || query || '%'
   OR r.description  ILIKE '%' || query || '%'
   OR similarity(coalesce(r.name, ''),         query) > 0.2
   OR similarity(coalesce(r.display_name, ''), query) > 0.2;
$$ LANGUAGE sql STABLE;

-- Recherche de permissions (name, display_name, module, action)
CREATE OR REPLACE FUNCTION search_admin_permission(query TEXT)
    RETURNS TABLE (id BIGINT, score REAL) AS $$
SELECT p.id,
       GREATEST(
               similarity(coalesce(p.name, ''),         query),
               similarity(coalesce(p.display_name, ''), query),
               similarity(coalesce(p.module, ''),       query),
               similarity(coalesce(p.action, ''),       query)
       ) AS score
FROM permission p
WHERE p.name         ILIKE '%' || query || '%'
   OR p.display_name ILIKE '%' || query || '%'
   OR p.module       ILIKE '%' || query || '%'
   OR p.action       ILIKE '%' || query || '%'
   OR similarity(coalesce(p.name, ''),   query) > 0.2
   OR similarity(coalesce(p.module, ''), query) > 0.2
   OR similarity(coalesce(p.action, ''), query) > 0.2;
$$ LANGUAGE sql STABLE;