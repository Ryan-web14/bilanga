

ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS source_rule_id  BIGINT;
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS measure_field   VARCHAR(50);
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS observed_value  DOUBLE PRECISION;
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS threshold_value DOUBLE PRECISION;

-- Analyser le déclenchement d'une règle donnée sur l'ensemble des diagnostics
CREATE INDEX IF NOT EXISTS idx_reco_source_rule
    ON recommendations(recommendation_type, source_rule_id);
