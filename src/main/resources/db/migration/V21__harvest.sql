-- ============================================================
-- V21 — Récoltes et économie de la parcelle
--
-- Le système dit ce qu'il faut faire. Il ne dit pas si cela a rapporté.
--
-- C'est ce qui manque pour QUANTIFIER l'apport de la plateforme au lieu de le
-- postuler — et une évaluation chiffrée vaut mieux qu'une démonstration
-- fonctionnelle. Avec les coûts déjà portés par `interventions` (V19) et la
-- surface plantée portée par `crops` (V16), cette table complète le triangle :
-- produit brut, charges, marge.
--
-- ⚠️ Ce que la mise en regard « conseils suivis / rendement obtenu » permettra
-- de dire reste un CONSTAT DESCRIPTIF. L'échantillon d'une exploitation
-- n'autorise aucune conclusion causale, et le service qui l'expose le dit
-- explicitement dans sa réponse.
-- ============================================================

CREATE TABLE IF NOT EXISTS harvests (
    id             BIGINT PRIMARY KEY,
    version        BIGINT       NOT NULL DEFAULT 0,

    plot_id        BIGINT       NOT NULL REFERENCES plots(id),

    -- Obligatoire, contrairement à interventions : une récolte sans culture
    -- rattachée ne se rapporte à aucune campagne et ne peut donc entrer dans
    -- aucun calcul de marge.
    crop_id        BIGINT       NOT NULL REFERENCES crops(id),

    quantity       DOUBLE PRECISION,
    unit           VARCHAR(20),

    quality        VARCHAR(20),

    harvested_at   DATE         NOT NULL,

    -- Prix unitaire, et non montant total : le total se recalcule, alors qu'un
    -- prix unitaire perdu ne se retrouve pas. Il permet en outre de comparer
    -- les campagnes à quantité différente.
    unit_price     NUMERIC(12, 2),

    -- Franc CFA d'Afrique centrale par défaut. La colonne existe pour que la
    -- lecture d'anciennes lignes reste sans ambiguïté si la monnaie change.
    currency       VARCHAR(3)   NOT NULL DEFAULT 'XAF',

    note           TEXT,

    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,

    CONSTRAINT chk_harvests_quantity   CHECK (quantity IS NULL OR quantity >= 0),
    CONSTRAINT chk_harvests_unit_price CHECK (unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT chk_harvests_quality
        CHECK (quality IS NULL OR quality IN ('EXCELLENTE', 'BONNE', 'MOYENNE', 'MEDIOCRE'))
);

-- Lecture chronologique par parcelle, comme partout ailleurs.
CREATE INDEX IF NOT EXISTS idx_harvests_plot_date
    ON harvests (plot_id, harvested_at DESC);

-- Agrégation par campagne : c'est l'unité de l'analyse économique.
CREATE INDEX IF NOT EXISTS idx_harvests_crop
    ON harvests (crop_id);
