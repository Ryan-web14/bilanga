-- ============================================================
-- V16 — Champs métier manquants et géolocalisation
--
-- Ces colonnes ne sont pas des formulaires de plus : ce sont les entrées qui
-- manquaient à des calculs que le système ne pouvait pas faire.
--
--   · latitude/longitude   : « Makotipoko » n'est pas exploitable par une
--                            machine. Sans coordonnées, ni météo, ni risque de
--                            voisinage, ni carte.
--   · irrigation_type      : le moteur conseille « irriguer » sans savoir si
--                            c'est possible. Sur une parcelle pluviale, ce
--                            conseil est inapplicable — et un conseil
--                            inapplicable est ce qui fait qu'un exploitant
--                            cesse de lire les recommandations.
--   · cycle_duration_days  : permet de DÉDUIRE le stade de croissance au lieu
--                            d'attendre une saisie. Aujourd'hui growth_stage se
--                            périme en silence et tout le moteur agronomique
--                            raisonne sur un stade faux.
--   · temperature_sol      : la colonne « temperature » est comparée aux seuils
--                            tempMin/tempMax de la culture — c'est donc l'air.
--                            Elle n'est PAS renommée ici (le contrat d'API et la
--                            feature map du microservice d'inférence en
--                            dépendent) ; on ajoute la mesure du sol, qui
--                            commande la germination et la tubérisation du
--                            manioc.
--   · last_seen_at         : la vue d'ensemble déduisait le silence d'un
--                            boîtier de l'absence de relevé, ce qui confond
--                            « boîtier muet » et « parcelle sans relevé ».
-- ============================================================

-- ------------------------------------------------------------
-- 1. plots — géolocalisation, irrigation, référence lisible
-- ------------------------------------------------------------
ALTER TABLE plots ADD COLUMN IF NOT EXISTS latitude        DOUBLE PRECISION;
ALTER TABLE plots ADD COLUMN IF NOT EXISTS longitude       DOUBLE PRECISION;
ALTER TABLE plots ADD COLUMN IF NOT EXISTS altitude        DOUBLE PRECISION;
ALTER TABLE plots ADD COLUMN IF NOT EXISTS irrigation_type VARCHAR(30);
ALTER TABLE plots ADD COLUMN IF NOT EXISTS plot_code       VARCHAR(40);

ALTER TABLE plots ADD CONSTRAINT chk_plots_latitude
    CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90));

ALTER TABLE plots ADD CONSTRAINT chk_plots_longitude
    CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180));

-- Le Congo culmine à un peu plus de 1000 m ; la borne large n'attrape que
-- l'absurde, à l'image des bornes de plausibilité de la V11.
ALTER TABLE plots ADD CONSTRAINT chk_plots_altitude
    CHECK (altitude IS NULL OR (altitude >= -500 AND altitude <= 9000));

ALTER TABLE plots ADD CONSTRAINT chk_plots_irrigation_type
    CHECK (irrigation_type IS NULL OR irrigation_type IN
           ('PLUVIAL', 'GOUTTE_A_GOUTTE', 'ASPERSION', 'MANUEL'));

-- Index partiel plutôt qu'unique simple : les parcelles héritées n'ont pas de
-- code et NULL ne doit pas entrer en collision avec NULL.
CREATE UNIQUE INDEX IF NOT EXISTS uq_plots_plot_code
    ON plots (plot_code) WHERE plot_code IS NOT NULL;

-- Attribution du code : une séquence de base plutôt qu'un comptage applicatif.
-- Deux créations simultanées produiraient sinon le même numéro.
CREATE SEQUENCE IF NOT EXISTS plot_code_seq START WITH 1 INCREMENT BY 1;

-- Recherche par proximité (risque de voisinage) : seules les parcelles
-- géolocalisées sont concernées.
CREATE INDEX IF NOT EXISTS idx_plots_coordinates
    ON plots (latitude, longitude) WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

-- ------------------------------------------------------------
-- 2. crops — cycle cultural, densité, traçabilité du lot
-- ------------------------------------------------------------
ALTER TABLE crops ADD COLUMN IF NOT EXISTS expected_harvest_date DATE;
ALTER TABLE crops ADD COLUMN IF NOT EXISTS cycle_duration_days   INTEGER;
ALTER TABLE crops ADD COLUMN IF NOT EXISTS planted_area          DOUBLE PRECISION;
ALTER TABLE crops ADD COLUMN IF NOT EXISTS plant_density         INTEGER;
ALTER TABLE crops ADD COLUMN IF NOT EXISTS seed_lot              VARCHAR(60);

ALTER TABLE crops ADD CONSTRAINT chk_crops_cycle_duration
    CHECK (cycle_duration_days IS NULL OR (cycle_duration_days > 0 AND cycle_duration_days <= 1000));

ALTER TABLE crops ADD CONSTRAINT chk_crops_planted_area
    CHECK (planted_area IS NULL OR planted_area > 0);

ALTER TABLE crops ADD CONSTRAINT chk_crops_plant_density
    CHECK (plant_density IS NULL OR plant_density > 0);

-- Un lot défaillant se repère en croisant les parcelles qui l'ont semé.
CREATE INDEX IF NOT EXISTS idx_crops_seed_lot
    ON crops (seed_lot) WHERE seed_lot IS NOT NULL;

-- ------------------------------------------------------------
-- 3. sensor_readings — mesures complémentaires
--
-- « temperature » reste la température de l'AIR : c'est elle que les moteurs
-- confrontent aux seuils tempMin/tempMax de la culture, et elle que le
-- microservice d'inférence reçoit sous la clé « temperature ».
-- ------------------------------------------------------------
ALTER TABLE sensor_readings ADD COLUMN IF NOT EXISTS temperature_sol         DOUBLE PRECISION;
ALTER TABLE sensor_readings ADD COLUMN IF NOT EXISTS pluviometrie            DOUBLE PRECISION;
ALTER TABLE sensor_readings ADD COLUMN IF NOT EXISTS conductivite_electrique DOUBLE PRECISION;
ALTER TABLE sensor_readings ADD COLUMN IF NOT EXISTS signal_strength         INTEGER;

COMMENT ON COLUMN sensor_readings.temperature IS
    'Température de l''AIR (°C). Comparée aux seuils tempMin/tempMax de la culture.';
COMMENT ON COLUMN sensor_readings.temperature_sol IS
    'Température du SOL (°C). Commande la germination et la tubérisation du manioc.';

-- Bornes larges, comme en V11 : le contrôle fin appartient à PlausibilityChecker,
-- qui marque le relevé sans le refuser — la trace de la panne a de la valeur.
ALTER TABLE sensor_readings ADD CONSTRAINT chk_readings_temperature_sol
    CHECK (temperature_sol IS NULL OR (temperature_sol >= -100 AND temperature_sol <= 200));

ALTER TABLE sensor_readings ADD CONSTRAINT chk_readings_pluviometrie
    CHECK (pluviometrie IS NULL OR (pluviometrie >= 0 AND pluviometrie <= 2000));

ALTER TABLE sensor_readings ADD CONSTRAINT chk_readings_conductivite
    CHECK (conductivite_electrique IS NULL OR conductivite_electrique >= 0);

-- dBm : négatif par nature, 0 signifie « non renseigné par le boîtier ».
ALTER TABLE sensor_readings ADD CONSTRAINT chk_readings_signal_strength
    CHECK (signal_strength IS NULL OR (signal_strength >= -150 AND signal_strength <= 0));

-- ------------------------------------------------------------
-- 4. iot_devices — gestion de parc
-- ------------------------------------------------------------
ALTER TABLE iot_devices ADD COLUMN IF NOT EXISTS last_seen_at     TIMESTAMP;
ALTER TABLE iot_devices ADD COLUMN IF NOT EXISTS firmware_version VARCHAR(40);
ALTER TABLE iot_devices ADD COLUMN IF NOT EXISTS installed_at     TIMESTAMP;
ALTER TABLE iot_devices ADD COLUMN IF NOT EXISTS battery_voltage  DOUBLE PRECISION;

-- La tension brute vieillit mieux que le pourcentage, dont la conversion dépend
-- du modèle de batterie.
ALTER TABLE iot_devices ADD CONSTRAINT chk_devices_battery_voltage
    CHECK (battery_voltage IS NULL OR (battery_voltage >= 0 AND battery_voltage <= 30));

-- Amorce : le dernier contact connu est, à défaut de mieux, le dernier relevé.
UPDATE iot_devices d
   SET last_seen_at = r.last_recorded_at
  FROM (select device_id, max(recorded_at) as last_recorded_at
          from sensor_readings
         where device_id is not null
         group by device_id) r
 WHERE r.device_id = d.id
   AND d.last_seen_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_devices_last_seen
    ON iot_devices (last_seen_at);

-- ------------------------------------------------------------
-- 5. alerts / recommendations — de l'information à l'action
--
-- Une alerte sans responsable désigné n'est traitée par personne, et un conseil
-- sans échéance n'engage à rien. Le cycle de vie existait (V8, V13) ; il lui
-- manquait un destinataire et un terme.
-- ------------------------------------------------------------
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS assigned_to BIGINT;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS due_at      TIMESTAMP;

ALTER TABLE alerts ADD CONSTRAINT fk_alert_assignee
    FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_alerts_assignee
    ON alerts (assigned_to, status) WHERE assigned_to IS NOT NULL;

-- Un conseil chiffré se compare à son bénéfice : c'est ce qui rendra possible
-- l'analyse de marge.
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS estimated_cost NUMERIC(12, 2);

ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_cost
    CHECK (estimated_cost IS NULL OR estimated_cost >= 0);
