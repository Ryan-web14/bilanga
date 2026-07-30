
CREATE TABLE IF NOT EXISTS disease_risk_condition (
                                                      id             BIGINT PRIMARY KEY,
                                                      crop_name      VARCHAR(50)  NOT NULL,
                                                      disease_code   VARCHAR(80)  NOT NULL,
                                                      measure_field  VARCHAR(50)  NOT NULL,
                                                      operator       VARCHAR(10)  NOT NULL,   -- > < >= <= BETWEEN
                                                      threshold      DOUBLE PRECISION NOT NULL,
                                                      threshold_max  DOUBLE PRECISION,        -- borne haute pour BETWEEN
                                                      weight         DOUBLE PRECISION NOT NULL DEFAULT 1,
                                                      label          VARCHAR(255) NOT NULL,
                                                      active         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_risk_condition_lookup
    ON disease_risk_condition(crop_name, disease_code);

-- ============================================================
-- TOMATE
-- ============================================================

-- Mildiou : humidité forte et températures modérées. La combinaison compte
-- davantage que chaque facteur pris isolément, d'où deux poids équivalents.
INSERT INTO disease_risk_condition
(id, crop_name, disease_code, measure_field, operator, threshold, threshold_max, weight, label) VALUES
                                                                                                    (1, 'tomate', 'Late_blight', 'humidite_air', '>',       80, NULL, 0.5, 'humidité de l''air supérieure à 80 %'),
                                                                                                    (2, 'tomate', 'Late_blight', 'temperature',  'BETWEEN', 15,   25, 0.5, 'température comprise entre 15 et 25 °C');

-- Alternariose : chaleur et humidité soutenues
INSERT INTO disease_risk_condition
(id, crop_name, disease_code, measure_field, operator, threshold, threshold_max, weight, label) VALUES
                                                                                                    (3, 'tomate', 'Early_blight', 'temperature',  '>', 24, NULL, 0.5, 'température supérieure à 24 °C'),
                                                                                                    (4, 'tomate', 'Early_blight', 'humidite_air', '>', 75, NULL, 0.5, 'humidité de l''air supérieure à 75 %');

-- Cladosporiose : humidité très élevée et ventilation insuffisante.
-- L'humidité pèse davantage, c'est le facteur déterminant.
INSERT INTO disease_risk_condition
(id, crop_name, disease_code, measure_field, operator, threshold, threshold_max, weight, label) VALUES
                                                                                                    (5, 'tomate', 'Leaf_Mold', 'humidite_air', '>',       85, NULL, 0.7, 'humidité de l''air supérieure à 85 %'),
                                                                                                    (6, 'tomate', 'Leaf_Mold', 'temperature',  'BETWEEN', 20,   25, 0.3, 'température comprise entre 20 et 25 °C');

-- TYLCV : transmis par l'aleurode, dont l'activité suit la chaleur
INSERT INTO disease_risk_condition
(id, crop_name, disease_code, measure_field, operator, threshold, threshold_max, weight, label) VALUES
                                                                                                    (7, 'tomate', 'Tomato_Yellow_Leaf_Curl_Virus', 'temperature',  '>', 28, NULL, 0.7, 'température supérieure à 28 °C'),
                                                                                                    (8, 'tomate', 'Tomato_Yellow_Leaf_Curl_Virus', 'humidite_air', '<', 70, NULL, 0.3, 'air sec, inférieur à 70 % d''humidité');

-- ============================================================
-- MANIOC
-- ============================================================

-- Bactériose : humidité et chaleur
INSERT INTO disease_risk_condition
(id, crop_name, disease_code, measure_field, operator, threshold, threshold_max, weight, label) VALUES
                                                                                                    (9,  'manioc', 'bacterial_blight', 'humidite_air', '>', 80, NULL, 0.5, 'humidité de l''air supérieure à 80 %'),
                                                                                                    (10, 'manioc', 'bacterial_blight', 'temperature',  '>', 28, NULL, 0.5, 'température supérieure à 28 °C');

-- Mosaïque et striure brune : transmises par l'aleurode,
-- dont les populations progressent par temps chaud
INSERT INTO disease_risk_condition
(id, crop_name, disease_code, measure_field, operator, threshold, threshold_max, weight, label) VALUES
                                                                                                    (11, 'manioc', 'mosaic_disease',       'temperature',  '>', 30, NULL, 0.7, 'température supérieure à 30 °C'),
                                                                                                    (12, 'manioc', 'mosaic_disease',       'humidite_air', '<', 75, NULL, 0.3, 'air sec, inférieur à 75 % d''humidité'),
                                                                                                    (13, 'manioc', 'brown_streak_disease', 'temperature',  '>', 27, NULL, 1.0, 'température supérieure à 27 °C');

-- Note : les maladies à transmission strictement mécanique ou par matériel
-- de plantation contaminé (mosaïque de la tomate, marbrure verte du manioc)
-- n'apparaissent pas ici : aucune condition environnementale mesurable ne
-- gouverne leur apparition.