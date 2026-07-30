-- ============================================================
-- V11 — Verrouillage du vocabulaire du domaine
--
-- Jusqu'ici, les colonnes à valeurs fermées (statuts, priorités, niveaux,
-- sources) étaient de simples VARCHAR sans contrainte : la base acceptait
-- « HAUT », « haute » et « URGENT » indifféremment. Une faute de frappe dans un
-- service passait donc inaperçue jusqu'à ce qu'une comparaison de chaînes
-- échoue silencieusement, très loin de la cause.
--
-- Les colonnes restent en VARCHAR — c'est le choix retenu, l'énumération Java
-- garde la frontière (DTO de requête) et la base garde l'invariant.
--
-- NOTE : les valeurs sont en FRANÇAIS pour les alertes (NOUVELLE, ACQUITTEE,
-- RESOLUE / MOYENNE, ELEVEE, CRITIQUE), conformément au code et à la V2.
-- crop_name n'est volontairement pas contraint : la base de connaissance
-- utilise le joker '*' pour les règles valables quelle que soit la culture.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Normalisation préalable des données existantes
--    Sans cela, une casse divergente ferait échouer l'ajout de la contrainte
--    et bloquerait le démarrage de l'application.
-- ------------------------------------------------------------
UPDATE plots               SET soil_type           = upper(trim(soil_type))           WHERE soil_type           IS NOT NULL;
UPDATE plots               SET status              = upper(trim(status))              WHERE status              IS NOT NULL;
UPDATE crops               SET status              = upper(trim(status))              WHERE status              IS NOT NULL;
UPDATE crops               SET growth_stage        = upper(trim(growth_stage))        WHERE growth_stage        IS NOT NULL;
UPDATE iot_devices         SET status              = upper(trim(status))              WHERE status              IS NOT NULL;
UPDATE sensors             SET status              = upper(trim(status))              WHERE status              IS NOT NULL;
UPDATE sensor_readings     SET quality             = upper(trim(quality))             WHERE quality             IS NOT NULL;
UPDATE ai_models           SET model_type          = upper(trim(model_type))          WHERE model_type          IS NOT NULL;
UPDATE ai_models           SET status              = upper(trim(status))              WHERE status              IS NOT NULL;
UPDATE diagnostics         SET source              = upper(trim(source))              WHERE source              IS NOT NULL;
UPDATE recommendations     SET recommendation_type = upper(trim(recommendation_type)) WHERE recommendation_type IS NOT NULL;
UPDATE recommendations     SET priority            = upper(trim(priority))            WHERE priority            IS NOT NULL;
UPDATE recommendations     SET status              = upper(trim(status))              WHERE status              IS NOT NULL;
UPDATE alerts              SET level               = upper(trim(level))               WHERE level               IS NOT NULL;
UPDATE alerts              SET status              = upper(trim(status))              WHERE status              IS NOT NULL;
UPDATE knowledge_rules     SET priority            = upper(trim(priority))            WHERE priority            IS NOT NULL;
UPDATE correlation_rules   SET priority            = upper(trim(priority))            WHERE priority            IS NOT NULL;

-- ------------------------------------------------------------
-- 2. Contraintes de vocabulaire
--    Toutes tolèrent NULL : une colonne facultative non renseignée reste valide.
-- ------------------------------------------------------------

-- Domaine FARM
ALTER TABLE plots ADD CONSTRAINT chk_plots_soil_type
    CHECK (soil_type IS NULL OR soil_type IN ('ARGILEUX', 'LIMONEUX', 'SABLEUX'));

ALTER TABLE plots ADD CONSTRAINT chk_plots_status
    CHECK (status IS NULL OR status IN ('ACTIVE', 'ARCHIVEE'));

ALTER TABLE crops ADD CONSTRAINT chk_crops_status
    CHECK (status IS NULL OR status IN ('EN_COURS', 'TERMINEE'));

ALTER TABLE crops ADD CONSTRAINT chk_crops_growth_stage
    CHECK (growth_stage IS NULL OR growth_stage IN
           ('LEVEE', 'CROISSANCE', 'FLORAISON', 'FRUCTIFICATION', 'MATURATION', 'TUBERISATION'));

-- Domaine IOT
ALTER TABLE iot_devices ADD CONSTRAINT chk_devices_status
    CHECK (status IS NULL OR status IN ('ACTIVE', 'RETIRE'));

ALTER TABLE sensors ADD CONSTRAINT chk_sensors_status
    CHECK (status IS NULL OR status IN ('ACTIVE', 'RETIRE'));

ALTER TABLE sensor_readings ADD CONSTRAINT chk_readings_quality
    CHECK (quality IS NULL OR quality IN ('TERRAIN', 'MANUELLE', 'SIMULEE'));

-- Domaine DIAGNOSIS
ALTER TABLE ai_models ADD CONSTRAINT chk_ai_models_type
    CHECK (model_type IS NULL OR model_type IN ('VISION', 'TABULAR'));

ALTER TABLE ai_models ADD CONSTRAINT chk_ai_models_status
    CHECK (status IS NULL OR status IN ('ACTIVE', 'RETIRE'));

ALTER TABLE diagnostics ADD CONSTRAINT chk_diagnostics_source
    CHECK (source IN ('IMAGE', 'CAPTEUR'));

-- Six valeurs, pas deux : le commentaire de la V2 n'annonçait que
-- « BASE | CORRELATION », mais les moteurs émettent aussi AGRONOMIQUE, RISQUE
-- et TENDANCE — ce sont les cinq sources de recommandation assemblées par
-- DiagnosisServiceImpl, plus l'arbitrage. Vérifié sur les données existantes.
ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_type
    CHECK (recommendation_type IS NULL OR recommendation_type IN
           ('BASE', 'AGRONOMIQUE', 'RISQUE', 'TENDANCE', 'CORRELATION', 'ARBITRAGE'));

ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_priority
    CHECK (priority IS NULL OR priority IN ('HAUTE', 'MOYENNE', 'BASSE'));

-- APPLIQUEE / IGNOREE ouvrent le retour de l'exploitant sur un conseil :
-- sans lui, rien ne permet de mesurer si le moteur conseille juste.
ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_status
    CHECK (status IS NULL OR status IN ('ACTIVE', 'APPLIQUEE', 'IGNOREE'));

ALTER TABLE alerts ADD CONSTRAINT chk_alerts_level
    CHECK (level IN ('MOYENNE', 'ELEVEE', 'CRITIQUE'));

ALTER TABLE alerts ADD CONSTRAINT chk_alerts_status
    CHECK (status IS NULL OR status IN ('NOUVELLE', 'ACQUITTEE', 'RESOLUE'));

-- Domaine KNOWLEDGE
ALTER TABLE knowledge_rules ADD CONSTRAINT chk_knowledge_rules_priority
    CHECK (priority IS NULL OR priority IN ('HAUTE', 'MOYENNE', 'BASSE'));

ALTER TABLE correlation_rules ADD CONSTRAINT chk_correlation_rules_priority
    CHECK (priority IS NULL OR priority IN ('HAUTE', 'MOYENNE', 'BASSE'));

-- L'opérateur pilote une comparaison exécutée en Java par RiskEngine :
-- une valeur inconnue y produit une condition silencieusement ignorée,
-- donc un score de risque faussement bas.
ALTER TABLE disease_risk_condition ADD CONSTRAINT chk_risk_condition_operator
    CHECK (operator IN ('>', '<', '>=', '<=', '==', 'BETWEEN'));

-- Un poids nul ou négatif fausserait la pondération du score de risque.
ALTER TABLE disease_risk_condition ADD CONSTRAINT chk_risk_condition_weight
    CHECK (weight IS NULL OR weight > 0);

-- ------------------------------------------------------------
-- 3. Bornes de plausibilité physique
--    Le contrôle applicatif (IngestServiceImpl.hasImplausibleValue) marque la
--    lecture comme anormale mais la conserve — c'est voulu. Ces bornes-ci sont
--    beaucoup plus larges : elles n'attrapent que l'absurde, ce qui ne peut
--    provenir que d'un défaut d'écriture et non d'une sonde qui dérive.
-- ------------------------------------------------------------
ALTER TABLE sensor_readings ADD CONSTRAINT chk_readings_ph
    CHECK (ph IS NULL OR (ph >= -50 AND ph <= 100));

ALTER TABLE sensor_readings ADD CONSTRAINT chk_readings_humidite_sol
    CHECK (humidite_sol IS NULL OR (humidite_sol >= -500 AND humidite_sol <= 1000));

ALTER TABLE sensor_readings ADD CONSTRAINT chk_readings_humidite_air
    CHECK (humidite_air IS NULL OR (humidite_air >= -500 AND humidite_air <= 1000));

ALTER TABLE iot_devices ADD CONSTRAINT chk_devices_battery
    CHECK (battery_level IS NULL OR (battery_level >= 0 AND battery_level <= 100));

-- Une confiance est une probabilité : hors [0,1] elle rendrait faux tout le
-- raisonnement de fiabilité (seuils 0.60 / 0.85 de ConfidenceEvaluator).
ALTER TABLE diagnostics ADD CONSTRAINT chk_diagnostics_confidence
    CHECK (confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1));
