-- ============================================================
-- V2 — Schéma MÉTIER agricole (projet BILANGA)
-- Dépend de V1 (référence users(id)).
-- PK en BIGINT (générateur Snowflake @IdGeneration).
-- ============================================================

-- ============================================================
-- DOMAINE FARM — parcelles et cultures
-- ============================================================
CREATE TABLE IF NOT EXISTS plots (
                                     id          BIGINT PRIMARY KEY,
                                     user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
                                     name        VARCHAR(150) NOT NULL,
                                     location    VARCHAR(255),
                                     soil_type   VARCHAR(50),                 -- ARGILEUX | LIMONEUX | SABLEUX
                                     area        DOUBLE PRECISION,            -- superficie (ha)
                                     status      VARCHAR(30) DEFAULT 'ACTIVE',
                                     created_at  TIMESTAMP NOT NULL DEFAULT now(),
                                     updated_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS crops (
                                     id            BIGINT PRIMARY KEY,
                                     plot_id       BIGINT NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
                                     crop_name     VARCHAR(50) NOT NULL,      -- TOMATE | MANIOC
                                     variety       VARCHAR(100),
                                     planting_date DATE,
                                     growth_stage  VARCHAR(50),
                                     status        VARCHAR(30) DEFAULT 'EN_COURS',
                                     created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- DOMAINE IOT — capteurs et mesures
-- ============================================================
CREATE TABLE IF NOT EXISTS iot_devices (
                                           id            BIGINT PRIMARY KEY,
                                           plot_id       BIGINT NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
                                           technical_id  VARCHAR(100) NOT NULL UNIQUE,
                                           device_name   VARCHAR(100),
                                           status        VARCHAR(30) DEFAULT 'ACTIVE',
                                           battery_level INTEGER,
                                           registered_at TIMESTAMP NOT NULL DEFAULT now(),
                                           updated_at    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sensors (
                                       id             BIGINT PRIMARY KEY,
                                       device_id      BIGINT NOT NULL REFERENCES iot_devices(id) ON DELETE CASCADE,
                                       sensor_type    VARCHAR(50) NOT NULL,
                                       status         VARCHAR(30) DEFAULT 'ACTIVE',
                                       default_value  DOUBLE PRECISION,
                                       added_at       TIMESTAMP NOT NULL DEFAULT now()
);

-- Une lecture = un relevé complet de la parcelle à un instant t
CREATE TABLE IF NOT EXISTS sensor_readings (
                                               id            BIGINT PRIMARY KEY,
                                               plot_id       BIGINT NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
                                               device_id     BIGINT REFERENCES iot_devices(id) ON DELETE SET NULL,
                                               temperature   DOUBLE PRECISION,
                                               humidite_sol  DOUBLE PRECISION,
                                               humidite_air  DOUBLE PRECISION,
                                               ph            DOUBLE PRECISION,
                                               azote         DOUBLE PRECISION,
                                               phosphore     DOUBLE PRECISION,
                                               potassium     DOUBLE PRECISION,
                                               luminosite    DOUBLE PRECISION,
                                               quality       VARCHAR(30),
                                               anomaly_detected BOOLEAN DEFAULT FALSE,
                                               recorded_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS observations (
                                            id           BIGINT PRIMARY KEY,
                                            plot_id      BIGINT NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
                                            user_id      BIGINT REFERENCES users(id) ON DELETE SET NULL,
                                            note         TEXT,
                                            photo_url    VARCHAR(255),
                                            observed_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- DOMAINE KNOWLEDGE — base de connaissance
-- ============================================================
CREATE TABLE IF NOT EXISTS crop_requirement (
                                                id                    BIGINT PRIMARY KEY,
                                                crop_name             VARCHAR(50) NOT NULL UNIQUE,
                                                ph_min                DOUBLE PRECISION,
                                                ph_max                DOUBLE PRECISION,
                                                hum_sol_min           DOUBLE PRECISION,
                                                hum_sol_max           DOUBLE PRECISION,
                                                temp_min              DOUBLE PRECISION,
                                                temp_max              DOUBLE PRECISION,
                                                azote_min             DOUBLE PRECISION,
                                                phosphore_min         DOUBLE PRECISION,
                                                potassium_min         DOUBLE PRECISION,
                                                tolerance_secheresse  DOUBLE PRECISION DEFAULT 0
);

CREATE TABLE IF NOT EXISTS knowledge_rules (
                                               id              BIGINT PRIMARY KEY,
                                               category        VARCHAR(50) NOT NULL,
                                               crop_name       VARCHAR(50) DEFAULT '*',
                                               condition_text  TEXT,
                                               proposed_action TEXT NOT NULL,
                                               priority        VARCHAR(20) DEFAULT 'MOYENNE',
                                               validated       BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS disease_knowledge (
                                                 id                   BIGINT PRIMARY KEY,
                                                 crop_name            VARCHAR(50) NOT NULL,
                                                 disease_code         VARCHAR(80) NOT NULL,
                                                 display_name         VARCHAR(150),
                                                 symptoms             TEXT,
                                                 favorable_conditions TEXT,
                                                 treatment            TEXT NOT NULL,
                                                 prevention           TEXT,
                                                 priority             VARCHAR(20) DEFAULT 'HAUTE',
                                                 CONSTRAINT uq_disease UNIQUE (crop_name, disease_code)
);

CREATE TABLE IF NOT EXISTS correlation_rules (
                                                 id                    BIGINT PRIMARY KEY,
                                                 crop_name             VARCHAR(50) DEFAULT '*',
                                                 disease_code          VARCHAR(80) DEFAULT '*',
                                                 measure_field         VARCHAR(50) NOT NULL,
                                                 operator              VARCHAR(5)  NOT NULL,
                                                 threshold             DOUBLE PRECISION NOT NULL,
                                                 extra_recommendation  TEXT NOT NULL,
                                                 priority              VARCHAR(20) DEFAULT 'HAUTE'
);

-- ============================================================
-- DOMAINE DIAGNOSIS — résultats
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_models (
                                         id              BIGINT PRIMARY KEY,
                                         name            VARCHAR(100) NOT NULL,
                                         model_type      VARCHAR(50),
                                         version         VARCHAR(30),
                                         precision_score DOUBLE PRECISION,
                                         trained_at      TIMESTAMP,
                                         status          VARCHAR(30) DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS diagnostics (
                                           id                BIGINT PRIMARY KEY,
                                           plot_id           BIGINT NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
                                           ai_model_id       BIGINT REFERENCES ai_models(id) ON DELETE SET NULL,
                                           reading_id        BIGINT REFERENCES sensor_readings(id) ON DELETE SET NULL,
                                           source            VARCHAR(20) NOT NULL,    -- IMAGE | CAPTEUR
                                           result            VARCHAR(80) NOT NULL,
                                           confidence_score  DOUBLE PRECISION,
                                           crop_name         VARCHAR(50),
                                           image_url         VARCHAR(255),
                                           diagnosed_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS recommendations (
                                               id                  BIGINT PRIMARY KEY,
                                               diagnostic_id       BIGINT NOT NULL REFERENCES diagnostics(id) ON DELETE CASCADE,
                                               content             TEXT NOT NULL,
                                               recommendation_type VARCHAR(50),          -- BASE | CORRELATION
                                               priority            VARCHAR(20) DEFAULT 'MOYENNE',
                                               status              VARCHAR(30) DEFAULT 'ACTIVE',
                                               created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS alerts (
                                      id            BIGINT PRIMARY KEY,
                                      plot_id       BIGINT NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
                                      diagnostic_id BIGINT REFERENCES diagnostics(id) ON DELETE SET NULL,
                                      level         VARCHAR(20) NOT NULL,
                                      message       TEXT,
                                      status        VARCHAR(30) DEFAULT 'NOUVELLE',
                                      created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- INDEX
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_readings_plot    ON sensor_readings(plot_id);
CREATE INDEX IF NOT EXISTS idx_diagnostics_plot ON diagnostics(plot_id);
CREATE INDEX IF NOT EXISTS idx_reco_diagnostic  ON recommendations(diagnostic_id);
CREATE INDEX IF NOT EXISTS idx_disease_lookup   ON disease_knowledge(crop_name, disease_code);