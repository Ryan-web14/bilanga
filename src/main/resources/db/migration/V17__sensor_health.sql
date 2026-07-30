-- ============================================================
-- V17 — Santé des sondes
--
-- Le contrôle de plausibilité (PlausibilityChecker) n'attrape que l'absurde :
-- pH 22, humidité 130 %. Or une sonde qui tombe en panne renvoie rarement une
-- valeur absurde. Elle se fige, elle dérive lentement, ou elle décroche de ses
-- voisines — en restant tout du long dans les bornes physiques.
--
-- C'est le seul angle mort du système qui puisse produire un conseil NUISIBLE.
-- Un diagnostic fondé sur une sonde qui dérive est faux, et il est présenté
-- avec exactement la même assurance qu'un diagnostic juste : la confiance du
-- modèle mesure la certitude de la prédiction, jamais la fiabilité de la mesure
-- qui l'a nourrie.
--
-- alerts.category sépare le technique de l'agronomique. Sans cette distinction,
-- « votre sonde d'humidité est bloquée » se retrouverait dans la même liste que
-- « risque de mildiou », alors que ces deux alertes ne s'adressent ni à la même
-- personne ni au même délai.
-- ============================================================

-- ------------------------------------------------------------
-- 1. iot_devices — verdict de santé
-- ------------------------------------------------------------
ALTER TABLE iot_devices ADD COLUMN IF NOT EXISTS sensor_health            VARCHAR(20) DEFAULT 'SAINE';
ALTER TABLE iot_devices ADD COLUMN IF NOT EXISTS sensor_health_reason     VARCHAR(300);
ALTER TABLE iot_devices ADD COLUMN IF NOT EXISTS sensor_health_checked_at TIMESTAMP;

-- Les boîtiers existants sont présumés sains : aucune analyse n'a encore tourné,
-- et les déclarer suspects sans preuve inhiberait le diagnostic sur tout le parc.
UPDATE iot_devices SET sensor_health = 'SAINE' WHERE sensor_health IS NULL;

ALTER TABLE iot_devices ADD CONSTRAINT chk_devices_sensor_health
    CHECK (sensor_health IS NULL OR sensor_health IN ('SAINE', 'SUSPECTE', 'DEFAILLANTE'));

-- La vue d'ensemble liste les boîtiers à remplacer : elle filtre sur le verdict.
CREATE INDEX IF NOT EXISTS idx_devices_sensor_health
    ON iot_devices (sensor_health) WHERE sensor_health <> 'SAINE';

-- ------------------------------------------------------------
-- 2. alerts — nature de l'alerte
--
-- Une panne de matériel n'est pas un conseil de culture. Les confondre revient
-- à noyer l'un dans l'autre : l'exploitant filtre les alertes agronomiques, le
-- technicien les alertes matérielles, et chacun cesse de lire celles de l'autre.
-- ------------------------------------------------------------
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS category VARCHAR(20) DEFAULT 'AGRONOMIQUE';

-- Tout l'existant provient du moteur de diagnostic, donc de l'agronomie.
UPDATE alerts SET category = 'AGRONOMIQUE' WHERE category IS NULL;

ALTER TABLE alerts ALTER COLUMN category SET NOT NULL;

ALTER TABLE alerts ADD CONSTRAINT chk_alerts_category
    CHECK (category IN ('AGRONOMIQUE', 'TECHNIQUE'));

-- Les deux publics consultent séparément : le filtre est sur (catégorie, statut).
CREATE INDEX IF NOT EXISTS idx_alerts_category_status
    ON alerts (category, status);
