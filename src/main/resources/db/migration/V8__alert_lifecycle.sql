

ALTER TABLE alerts ADD COLUMN IF NOT EXISTS signature       VARCHAR(160);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMP;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS resolved_at     TIMESTAMP;

-- Recherche des alertes ouvertes d'une parcelle, du plus récent au plus ancien
CREATE INDEX IF NOT EXISTS idx_alerts_plot_status
    ON alerts(plot_id, status, created_at DESC);

-- Détection des doublons : la même situation, toujours ouverte
CREATE INDEX IF NOT EXISTS idx_alerts_signature
    ON alerts(plot_id, signature, status);
