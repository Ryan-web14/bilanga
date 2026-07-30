-- ============================================================
-- V19 — Journal d'interventions
--
-- Le chaînon manquant. Le système conseille, et ne sait pas ce qui a été fait.
-- `observations` ne porte qu'une note libre ; `recommendations.status =
-- APPLIQUEE` dit qu'un conseil a été suivi, sans dire comment, ni quand, ni à
-- quelle dose, ni pour quel coût.
--
-- Ce que cette table ouvre :
--   · la BOUCLE COMPLÈTE conseil → intervention → mesure de l'effet. Aujourd'hui
--     la chaîne s'arrête au conseil, et rien ne permet de dire si le système
--     sert à quelque chose ;
--   · l'EFFICACITÉ RÉELLE des traitements, en comparant les mesures avant et
--     après. Le système ne se contente plus de conseiller : il évalue ses
--     propres conseils ;
--   · la TRAÇABILITÉ DES INTRANTS, condition de toute démarche de certification ;
--   · les COÛTS, base de l'analyse économique (V21).
--
-- recommendation_id est NULLABLE, et c'est essentiel : les exploitants agissent
-- aussi de leur propre chef, et ce sont précisément ces actions-là qui
-- expliquent des évolutions que le système ne comprendrait pas autrement.
-- ============================================================

CREATE TABLE IF NOT EXISTS interventions (
    id                BIGINT PRIMARY KEY,
    version           BIGINT       NOT NULL DEFAULT 0,

    plot_id           BIGINT       NOT NULL REFERENCES plots(id),

    -- La culture peut manquer : un désherbage d'inter-campagne se fait sur une
    -- parcelle sans plantation en cours.
    crop_id           BIGINT REFERENCES crops(id) ON DELETE SET NULL,

    -- Le conseil à l'origine de l'action, quand il y en a un.
    recommendation_id BIGINT REFERENCES recommendations(id) ON DELETE SET NULL,

    type              VARCHAR(20)  NOT NULL,

    -- Nom du produit employé : engrais, fongicide, semence.
    product           VARCHAR(150),
    dose              DOUBLE PRECISION,
    unit              VARCHAR(20),

    cost              NUMERIC(12, 2),

    performed_at      TIMESTAMP    NOT NULL,
    performed_by      BIGINT REFERENCES users(id) ON DELETE SET NULL,

    -- Conditions au moment de l'intervention. Traiter avant une pluie annonce un
    -- lessivage : sans cette note, l'absence d'effet resterait inexpliquée.
    weather_note      VARCHAR(300),
    note              TEXT,

    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP,

    CONSTRAINT chk_interventions_type
        CHECK (type IN ('IRRIGATION', 'FERTILISATION', 'TRAITEMENT',
                        'DESHERBAGE', 'SEMIS', 'RECOLTE', 'AUTRE')),

    CONSTRAINT chk_interventions_dose CHECK (dose IS NULL OR dose >= 0),
    CONSTRAINT chk_interventions_cost CHECK (cost IS NULL OR cost >= 0)
);

-- Lecture chronologique par parcelle : c'est l'accès dominant, comme pour
-- sensor_readings (V5) et diagnostics.
CREATE INDEX IF NOT EXISTS idx_interventions_plot_date
    ON interventions (plot_id, performed_at DESC);

-- Agrégation des charges par campagne (V21).
CREATE INDEX IF NOT EXISTS idx_interventions_crop
    ON interventions (crop_id) WHERE crop_id IS NOT NULL;

-- « Ce conseil a-t-il été suivi, et comment ? » — jointure du retour d'usage.
CREATE INDEX IF NOT EXISTS idx_interventions_recommendation
    ON interventions (recommendation_id) WHERE recommendation_id IS NOT NULL;

-- Analyse d'efficacité par type de traitement, toutes parcelles confondues.
CREATE INDEX IF NOT EXISTS idx_interventions_type_date
    ON interventions (type, performed_at DESC);
