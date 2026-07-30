

CREATE TABLE IF NOT EXISTS recommendation_arbitration (
                                                          id          BIGINT PRIMARY KEY,
                                                          crop_name   VARCHAR(50)  NOT NULL DEFAULT '*',
                                                          category_a  VARCHAR(50)  NOT NULL,
                                                          category_b  VARCHAR(50)  NOT NULL,
                                                          synthesis   TEXT         NOT NULL,
                                                          priority    VARCHAR(20)  NOT NULL DEFAULT 'HAUTE',
                                                          active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_arbitration_crop
    ON recommendation_arbitration(crop_name);

-- ============================================================
-- Arbitrages
-- ============================================================

INSERT INTO recommendation_arbitration
(id, crop_name, category_a, category_b, synthesis, priority) VALUES

-- Le cas rencontré en test : maladie foliaire et déficit hydrique simultanés.
-- Les deux conseils visent des compartiments différents — l'air et le sol —
-- et se concilient par la technique d'apport.
(1, '*', 'MALADIE_FOLIAIRE', 'STRESS_HYDRIQUE',
 'Les besoins en eau et la lutte contre la maladie foliaire se concilient par la technique d''arrosage : '
     || 'irriguez au pied, jamais par aspersion, tôt le matin afin que le feuillage soit sec dans la journée. '
     || 'Le sol reçoit l''eau nécessaire sans que l''humidité s''installe autour des feuilles.',
 'HAUTE'),

(2, '*', 'RISQUE_MALADIE', 'STRESS_HYDRIQUE',
 'Le besoin d''irrigation ne contredit pas la prévention sanitaire : apportez l''eau au pied, en début de journée, '
     || 'et espacez les apports plutôt que de les fractionner, afin de limiter la durée d''humectation du feuillage.',
 'HAUTE'),

-- Excès d'eau et maladie : même cause, une seule action les traite
(3, '*', 'MALADIE_FOLIAIRE', 'EXCES_EAU',
 'L''excès d''eau entretient directement la maladie détectée. Traitez la cause avant le symptôme : '
     || 'suspendez l''irrigation et améliorez le drainage, le traitement phytosanitaire n''aura d''effet durable '
     || 'qu''une fois l''humidité maîtrisée.',
 'HAUTE'),

-- Sol acide et carence : l'ordre des interventions compte
(4, '*', 'CARENCES_NUTRITIVES', 'SOL_ACIDE',
 'Corrigez le pH avant de fertiliser. En sol acide, le phosphore se lie au fer et à l''aluminium et devient '
     || 'inassimilable : un apport d''engrais réalisé avant le chaulage sera en grande partie perdu.',
 'HAUTE'),

(5, '*', 'CARENCES_NUTRITIVES', 'SOL_ALCALIN',
 'Corrigez le pH avant de fertiliser. En sol alcalin, le fer, le manganèse et le zinc deviennent peu disponibles ; '
     || 'un apport d''engrais réalisé avant l''amendement organique donnera peu de résultat.',
 'HAUTE'),

-- Stress évaporatif et déficit hydrique : ne pas surdoser l'irrigation
(6, '*', 'STRESS_HYDRIQUE', 'STRESS_THERMIQUE',
 'La demande évaporative est le facteur dominant : augmenter les apports d''eau ne suffira pas si la plante '
     || 'perd plus qu''elle n''absorbe. Cherchez d''abord à réduire la contrainte — ombrage aux heures chaudes, '
     || 'paillage pour limiter l''évaporation du sol — avant d''accroître l''irrigation.',
 'HAUTE');

-- ============================================================
-- Levée d'ambiguïtés dans les textes existants
--
-- Plusieurs conseils mentionnaient « l'humidité » sans préciser s'il
-- s'agissait de celle de l'air ou du sol, ce qui les rendait inutilement
-- difficiles à concilier entre eux.
-- ============================================================

UPDATE disease_knowledge
SET treatment = 'Améliorer l''aération entre les plants et abaisser l''humidité de l''air ambiant. '
    || 'Retirer les feuilles atteintes. Appliquer un fongicide si la progression se poursuit.'
WHERE crop_name = 'tomate' AND disease_code = 'Leaf_Mold';

UPDATE disease_knowledge
SET treatment = 'Retirer et détruire les parties atteintes. Traiter rapidement au fongicide. '
    || 'Améliorer l''aération entre les plants et éviter d''humecter le feuillage lors des arrosages.'
WHERE crop_name = 'tomate' AND disease_code = 'Late_blight';

UPDATE knowledge_rules
SET proposed_action = 'Irriguer la parcelle rapidement, au pied des plants et de préférence tôt le matin, '
    || 'afin de limiter l''évaporation et de laisser le feuillage sécher dans la journée. '
    || 'Surveiller l''humidité du sol les jours suivants.'
WHERE category = 'STRESS_HYDRIQUE';

UPDATE knowledge_rules
SET proposed_action = 'Renforcer la surveillance sanitaire de la parcelle. Améliorer l''aération pour abaisser '
    || 'l''humidité de l''air, éviter d''humecter le feuillage, et envisager un traitement préventif.'
WHERE category = 'RISQUE_MALADIE';