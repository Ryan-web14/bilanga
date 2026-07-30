-- ============================================================
-- V29 — itinéraire technique : ce qui était PRÉVU sur une campagne
--
-- LE MANQUE COMBLÉ
--
-- Le système enregistre ce qui a été FAIT (`interventions`, V19) et ce qu'il
-- CONSEILLE (`recommendations`). Il ne sait rien de ce qui était PRÉVU.
--
-- Or c'est le troisième terme qui rend les deux autres lisibles. Sans lui :
--   · une opération oubliée est indiscernable d'une opération jamais planifiée ;
--   · le coût prévisionnel d'une campagne ne se calcule pas avant la récolte ;
--   · « il fallait traiter au stade floraison » reste dans la tête de
--     l'exploitant, et disparaît avec lui.
--
-- ------------------------------------------------------------
-- LE VOCABULAIRE EST CELUI DE InterventionType — délibérément
--
-- `type` reprend EXACTEMENT les valeurs de interventions.type. Le rapprochement
-- prévu ↔ réalisé se fait sur (crop_id, type) : deux vocabulaires distincts
-- rendraient l'appariement impossible, et il n'y aurait aucun moyen de s'en
-- apercevoir — les listes seraient simplement toujours vides.
--
-- ⚠️ Ajouter une valeur à InterventionType impose donc de reprendre LES DEUX
-- contraintes CHECK, ici et sur `interventions` (V19).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crop_planned_operations (
    id                  BIGINT       PRIMARY KEY,
    version             BIGINT       NOT NULL DEFAULT 0,

    crop_id             BIGINT       NOT NULL REFERENCES crops(id) ON DELETE CASCADE,

    type                VARCHAR(20)  NOT NULL,
    label               VARCHAR(150),

    -- ------------------------------------------------------------
    -- DEUX MANIÈRES DE DATER, et c'est volontaire
    --
    -- planned_on         : une date ferme (« le 12 mai »)
    -- days_after_planting: une position dans le cycle (« J+45 »)
    --
    -- La seconde est celle d'un itinéraire réutilisable : elle survit au
    -- clonage vers une campagne plantée un autre jour, là où une date ferme
    -- devrait être ressaisie ligne par ligne. Le mapper résout J+n en date
    -- réelle à la LECTURE, depuis crops.planting_date — un calcul persisté
    -- deviendrait faux dès qu'on corrige la date de plantation.
    -- ------------------------------------------------------------
    planned_on          DATE,
    days_after_planting INT,

    -- Indicatif : à quel stade l'opération est censée tomber. Non contraint au
    -- vocabulaire des stades — un itinéraire peut viser un stade qu'aucune
    -- table de seuils ne connaît, et le refuser n'apporterait rien.
    growth_stage        VARCHAR(30),

    product             VARCHAR(150),
    dose                DOUBLE PRECISION,
    unit                VARCHAR(20),

    -- Coût PRÉVU. À ne pas confondre avec interventions.cost, qui est constaté.
    -- Leur écart est précisément ce qu'on veut pouvoir lire.
    estimated_cost      NUMERIC(12,2),

    -- ------------------------------------------------------------
    -- Le rapprochement, quand il est CONFIRMÉ
    --
    -- ⚠️ Cette colonne ne porte QUE les rapprochements manuels ou explicitement
    -- validés. Les rapprochements automatiques sont calculés à la lecture et
    -- JAMAIS écrits ici : un mauvais appariement qui s'écrit se propage et
    -- devra être corrigé à la main ; un mauvais appariement qui se recalcule
    -- disparaît dès que la donnée s'améliore.
    -- ------------------------------------------------------------
    intervention_id     BIGINT       REFERENCES interventions(id) ON DELETE SET NULL,
    matched_at          TIMESTAMP,
    match_confidence    VARCHAR(20),

    status              VARCHAR(20)  NOT NULL DEFAULT 'PREVUE',
    note                TEXT,

    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP,

    -- Une opération qu'on ne sait pas dater n'est pas un plan, c'est une note.
    CONSTRAINT chk_planned_op_when CHECK (
        planned_on IS NOT NULL OR days_after_planting IS NOT NULL),

    -- Miroir exact de InterventionType — voir l'avertissement en tête.
    CONSTRAINT chk_planned_op_type CHECK (type IN
        ('IRRIGATION', 'FERTILISATION', 'TRAITEMENT', 'DESHERBAGE',
         'SEMIS', 'RECOLTE', 'AUTRE')),

    CONSTRAINT chk_planned_op_status CHECK (status IN
        ('PREVUE',        -- planifiée, rien de constaté
         'REALISEE',      -- une intervention l'a satisfaite
         'PARTIELLE',     -- faite, mais pas comme prévu (dose, date)
         'ABANDONNEE')),  -- décidé de ne pas la faire

    CONSTRAINT chk_planned_op_confidence CHECK (match_confidence IS NULL OR match_confidence IN
        ('EXACTE',        -- à ±2 jours de la date prévue
         'PROBABLE',      -- à ±10 jours
         'MANUELLE'))     -- rapprochement confirmé par un humain
);

-- ⚠️ AUCUN statut 'EN_RETARD'.
--
-- C'est un état DÉRIVÉ : planned_on < aujourd'hui ET aucune intervention
-- rapprochée. Le persister obligerait à le rafraîchir — ce que rien ne fait
-- dans ce projet, qui n'a ni ordonnanceur ni tâche de fond — et il serait donc
-- faux dès le lendemain de son écriture. Il est calculé dans le mapper.

-- L'itinéraire d'une campagne, dans l'ordre chronologique.
CREATE INDEX IF NOT EXISTS idx_planned_op_crop
    ON crop_planned_operations (crop_id, planned_on);

-- Une intervention réelle ne peut pas satisfaire deux opérations prévues.
-- Partiel, car NULL ne doit pas entrer en collision avec NULL : la majorité des
-- lignes sont non rapprochées, et un index total les refuserait toutes sauf une.
CREATE UNIQUE INDEX IF NOT EXISTS uq_planned_op_intervention
    ON crop_planned_operations (intervention_id)
    WHERE intervention_id IS NOT NULL;

COMMENT ON TABLE crop_planned_operations IS
    'Itinéraire technique : les opérations PRÉVUES d''une campagne, à confronter '
    'aux interventions réellement menées. Le vocabulaire de `type` est celui de '
    'InterventionType — les faire diverger rendrait le rapprochement impossible.';

COMMENT ON COLUMN crop_planned_operations.intervention_id IS
    'Rapprochement CONFIRMÉ uniquement. Les rapprochements automatiques sont '
    'calculés à chaque lecture par ItineraryMatcher et ne sont jamais persistés.';

COMMENT ON COLUMN crop_planned_operations.days_after_planting IS
    'Position dans le cycle (J+n). Résolue en date à la LECTURE depuis '
    'crops.planting_date : la persister la rendrait fausse dès qu''on corrige '
    'la date de plantation. C''est la forme qui survit au clonage.';
