-- ============================================================
-- V13 — Cycle de vie complet des alertes
--
-- Jusqu'ici une alerte ne se refermait que si un humain la résolvait à la main.
-- Rien ne la fermait quand la situation redevenait normale : les alertes
-- s'accumulaient en statut NOUVELLE, et overallStatus restait bloqué sur
-- ALERTE / CRITIQUE indéfiniment — au point de rendre le tableau de bord
-- inutilisable au bout de quelques jours.
--
-- Trois colonnes suffisent à corriger cela sans introduire d'ordonnanceur :
--
--   · last_seen_at      : dernière fois que la situation a été reconstatée.
--                         Une alerte que plus aucun diagnostic ne reproduit
--                         est une alerte périmée.
--   · resolution_reason : distingue une résolution humaine d'une fermeture
--                         automatique. Sans cette distinction, on ne peut plus
--                         dire si quelqu'un est intervenu ou si le problème a
--                         cessé de lui-même.
--   · escalation_count  : nombre de fois où la situation a été reconstatée sans
--                         acquittement. C'est ce qui permet de monter le niveau
--                         d'une alerte ignorée, au lieu de la laisser dormir.
-- ============================================================

ALTER TABLE alerts ADD COLUMN IF NOT EXISTS last_seen_at      TIMESTAMP;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS resolution_reason VARCHAR(40);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS escalation_count  INTEGER NOT NULL DEFAULT 0;

-- Les alertes déjà en base n'ont jamais été revues : leur création fait foi.
UPDATE alerts SET last_seen_at = created_at WHERE last_seen_at IS NULL;

ALTER TABLE alerts ADD CONSTRAINT chk_alerts_resolution_reason
    CHECK (resolution_reason IS NULL OR resolution_reason IN
           ('RESOLUE_MANUELLEMENT', 'AUTO_SITUATION_NORMALISEE', 'AUTO_SITUATION_REMPLACEE'));

ALTER TABLE alerts ADD CONSTRAINT chk_alerts_escalation_count
    CHECK (escalation_count >= 0);

-- La réconciliation cherche les alertes ouvertes d'une parcelle pour une source
-- donnée ; sans index, ce balayage se ferait à chaque diagnostic.
CREATE INDEX IF NOT EXISTS idx_alerts_plot_status_signature
    ON alerts (plot_id, status, signature);
