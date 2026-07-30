-- ============================================================
-- V20 — Cache des prévisions météo
--
-- Le moteur raisonne exclusivement sur le PASSÉ MESURÉ. Il ignore ce qui
-- arrive. C'est la limite qui produit ses conseils les plus absurdes :
--
--   « Humidité du sol à 24 %, irriguez sans délai » — alors que 18 mm de pluie
--   tombent dans six heures. L'exploitant se déplace, arrose, et la parcelle
--   est saturée le soir même.
--
--   « Traitez contre le mildiou » — deux heures avant une averse qui lessivera
--   le produit. Le traitement est perdu, et son inefficacité sera mise au
--   compte du produit ou du système.
--
-- Une table plutôt qu'un cache en mémoire : le fournisseur limite le nombre
-- d'appels, et la prévision d'il y a vingt minutes reste valable. Elle survit
-- aussi au redémarrage, ce qui évite une rafale d'appels au démarrage.
--
-- ⚠️ Le système DOIT rester utilisable sans météo. C'est la règle appliquée au
-- microservice d'inférence, et elle vaut ici : une prévision indisponible
-- retire une capacité, elle ne casse rien.
-- ============================================================

CREATE TABLE IF NOT EXISTS weather_forecast (
    id               BIGINT PRIMARY KEY,

    plot_id          BIGINT           NOT NULL REFERENCES plots(id) ON DELETE CASCADE,

    -- Échéance visée par la prévision.
    forecast_at      TIMESTAMP        NOT NULL,

    -- Quand elle a été obtenue : c'est l'âge du cache, pas la validité de la
    -- prévision. Une prévision de demain obtenue il y a trois jours ne vaut pas
    -- la même obtenue il y a une heure.
    fetched_at       TIMESTAMP        NOT NULL DEFAULT now(),

    temperature      DOUBLE PRECISION,
    humidite         DOUBLE PRECISION,
    precipitation_mm DOUBLE PRECISION,
    wind_speed       DOUBLE PRECISION,
    cloud_cover      DOUBLE PRECISION,

    provider         VARCHAR(30),

    CONSTRAINT chk_forecast_humidite
        CHECK (humidite IS NULL OR (humidite >= 0 AND humidite <= 100)),
    CONSTRAINT chk_forecast_precipitation
        CHECK (precipitation_mm IS NULL OR precipitation_mm >= 0),
    CONSTRAINT chk_forecast_cloud_cover
        CHECK (cloud_cover IS NULL OR (cloud_cover >= 0 AND cloud_cover <= 100)),

    -- Une échéance, une ligne : le rafraîchissement écrase au lieu d'empiler.
    -- Sans cette contrainte, un appel par heure ferait grossir la table
    -- indéfiniment avec des doublons.
    CONSTRAINT uq_forecast_plot_moment UNIQUE (plot_id, forecast_at)
);

-- Lecture dominante : « que prévoit-on pour cette parcelle dans les N heures ».
CREATE INDEX IF NOT EXISTS idx_forecast_plot_moment
    ON weather_forecast (plot_id, forecast_at);

-- Purge des échéances dépassées : une prévision d'hier n'a plus d'usage.
CREATE INDEX IF NOT EXISTS idx_forecast_fetched
    ON weather_forecast (fetched_at);

-- ------------------------------------------------------------
-- Le sixième moteur émet un type de recommandation de plus
--
-- La contrainte de la V11 énumérait les cinq moteurs d'alors plus l'arbitrage.
-- Sans cette reprise, la première recommandation météo ferait échouer son
-- insertion — et, comme elle survient au cœur du diagnostic, ferait perdre
-- le diagnostic entier.
-- ------------------------------------------------------------
ALTER TABLE recommendations DROP CONSTRAINT IF EXISTS chk_recommendations_type;

ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_type
    CHECK (recommendation_type IS NULL OR recommendation_type IN
           ('BASE', 'AGRONOMIQUE', 'RISQUE', 'TENDANCE', 'CORRELATION', 'ARBITRAGE', 'METEO'));
