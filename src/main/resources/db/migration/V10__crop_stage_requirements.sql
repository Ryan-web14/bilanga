-- ============================================================
-- V10 — Seuils agronomiques par stade de croissance
--
-- Les seuils de crop_requirement valent pour la culture en général. Or les
-- besoins d'une plante varient fortement au cours de son cycle : une tomate
-- en fructification consomme davantage d'eau et de potassium qu'un plant en
-- levée, et un excès d'azote au moment de la tubérisation du manioc pousse
-- le feuillage au détriment des tubercules.
--
-- Cette table ne porte que les écarts. Une colonne nulle signifie que le
-- stade n'infléchit pas ce seuil, et la valeur de crop_requirement s'applique.
--
-- AVERTISSEMENT : valeurs indicatives, à faire valider auprès de sources
-- agronomiques avant toute exploitation en production.
-- ============================================================

CREATE TABLE IF NOT EXISTS crop_stage_requirement (
    id                   BIGINT PRIMARY KEY,
    crop_name            VARCHAR(50) NOT NULL,
    growth_stage         VARCHAR(50) NOT NULL,
    label                VARCHAR(255),

    ph_min               DOUBLE PRECISION,
    ph_max               DOUBLE PRECISION,
    hum_sol_min          DOUBLE PRECISION,
    hum_sol_max          DOUBLE PRECISION,
    temp_min             DOUBLE PRECISION,
    temp_max             DOUBLE PRECISION,
    azote_min            DOUBLE PRECISION,
    phosphore_min        DOUBLE PRECISION,
    potassium_min        DOUBLE PRECISION,
    tolerance_secheresse DOUBLE PRECISION,

    CONSTRAINT uq_crop_stage UNIQUE (crop_name, growth_stage)
);

CREATE INDEX IF NOT EXISTS idx_crop_stage_lookup
    ON crop_stage_requirement(crop_name, growth_stage);

-- ============================================================
-- TOMATE
-- ============================================================
INSERT INTO crop_stage_requirement
    (id, crop_name, growth_stage, label, hum_sol_min, hum_sol_max,
     azote_min, phosphore_min, potassium_min) VALUES

-- Système racinaire encore superficiel : le sol doit rester constamment humide,
-- mais les besoins nutritifs restent modestes.
(1, 'tomate', 'LEVEE', 'Levée et jeunes plants', 65, 85, 25, 15, 25),

-- Édification du feuillage : c'est le moment où l'azote compte le plus.
(2, 'tomate', 'CROISSANCE', 'Croissance végétative', 60, 80, 45, 18, 30),

-- La floraison mobilise le phosphore ; un excès d'azote à ce stade prolonge
-- la végétation au détriment de la mise à fruit.
(3, 'tomate', 'FLORAISON', 'Floraison', 65, 80, 30, 28, 40),

-- Demande en eau maximale, et le potassium gouverne le calibre et la fermeté.
(4, 'tomate', 'FRUCTIFICATION', 'Grossissement des fruits', 70, 85, 30, 20, 50),

-- On restreint l'eau pour concentrer les sucres et limiter l'éclatement.
(5, 'tomate', 'MATURATION', 'Maturation', 55, 70, 20, 15, 45);

-- ============================================================
-- MANIOC
-- ============================================================
INSERT INTO crop_stage_requirement
    (id, crop_name, growth_stage, label, hum_sol_min, hum_sol_max,
     azote_min, phosphore_min, potassium_min, tolerance_secheresse) VALUES

-- La rusticité du manioc tient à son système racinaire profond. Une bouture
-- fraîchement plantée n'en dispose pas encore : sa tolérance à la sécheresse
-- est bien inférieure à celle de la culture établie.
(6, 'manioc', 'LEVEE', 'Reprise des boutures', 50, 70, 15, 12, 20, 0.2),

(7, 'manioc', 'CROISSANCE', 'Croissance végétative', 45, 70, 30, 12, 25, 0.5),

-- Le potassium gouverne la tubérisation. À l'inverse, un excès d'azote à ce
-- stade entretient le feuillage aux dépens des tubercules.
(8, 'manioc', 'TUBERISATION', 'Formation des tubercules', 45, 70, 15, 15, 40, 0.6),

(9, 'manioc', 'MATURATION', 'Maturation', 35, 60, 10, 10, 35, 0.8);
