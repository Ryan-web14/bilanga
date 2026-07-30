-- ============================================================
-- V12 — Verrouillage optimiste
--
-- Aucune entité ne portait de colonne de version. Deux écritures concurrentes
-- sur le même enregistrement s'écrasaient donc en silence, la dernière gagnant
-- sans que personne l'apprenne. Les cas ne sont pas théoriques :
--
--   · une alerte acquittée par deux exploitants en même temps ;
--   · la batterie d'un boîtier mise à jour par une ingestion pendant qu'un
--     administrateur modifie le même boîtier ;
--   · un seuil agronomique édité à deux mains depuis la console d'admin.
--
-- Avec @Version, la seconde écriture échoue proprement et remonte en 409
-- CONCURRENT_MODIFICATION (voir GlobalExceptionHandler).
--
-- Les tables purement historiques — diagnostics, sensor_readings, observations,
-- audit_log — en sont exemptées : elles ne sont jamais mises à jour, seulement
-- insérées puis lues.
-- ============================================================

-- Domaine FARM
ALTER TABLE plots                    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE crops                    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Domaine IOT
ALTER TABLE iot_devices              ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sensors                  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Domaine DIAGNOSIS — cycle de vie
ALTER TABLE alerts                   ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE recommendations          ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Domaine KNOWLEDGE — édité depuis la console d'administration
ALTER TABLE crop_requirement         ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE crop_stage_requirement   ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_rules          ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE disease_knowledge        ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE disease_risk_condition   ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE correlation_rules        ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE recommendation_arbitration ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
