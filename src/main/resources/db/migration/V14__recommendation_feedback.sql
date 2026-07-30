-- ============================================================
-- V14 — Retour de l'exploitant sur les conseils
--
-- La colonne `status` existait depuis la V2 avec la valeur ACTIVE, et rien ne
-- la faisait jamais changer : tous les conseils naissaient à traiter et y
-- restaient. Impossible, dans ces conditions, de répondre à la seule question
-- qui valide un système expert — « ses conseils sont-ils suivis ? ».
--
-- La V11 a ouvert le vocabulaire (ACTIVE | APPLIQUEE | IGNOREE). Deux colonnes
-- de plus suffisent à rendre le retour exploitable :
--
--   · feedback_at   : quand la réponse a été donnée. Un conseil appliqué trois
--                     jours après son émission n'a pas la même portée qu'un
--                     conseil appliqué dans l'heure.
--   · feedback_note : le motif. C'est sur les conseils écartés qu'il compte —
--                     c'est là que se trouve ce qui permettra d'amender la règle.
-- ============================================================

ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS feedback_at   TIMESTAMP;
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS feedback_note VARCHAR(500);

-- Un conseil tranché porte forcément la date de sa réponse ; un conseil encore
-- à traiter ne peut pas en porter.
ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_feedback
    CHECK ((status = 'ACTIVE' AND feedback_at IS NULL)
        OR (status <> 'ACTIVE' AND feedback_at IS NOT NULL)
        OR status IS NULL);

-- Le suivi interroge les conseils par parcelle et par statut.
CREATE INDEX IF NOT EXISTS idx_recommendations_status
    ON recommendations (status, recommendation_type);
