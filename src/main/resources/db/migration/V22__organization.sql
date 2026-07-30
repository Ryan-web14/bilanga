-- ============================================================
-- V22 — Niveau d'organisation : exploitation et coopérative
--
-- Le modèle était « Plot → Users » : un exploitant, ses parcelles. Il ne
-- représente pas ce que le terrain pratique :
--
--   · une EXPLOITATION regroupant des parcelles dispersées, parfois à plusieurs
--     kilomètres les unes des autres ;
--   · une COOPÉRATIVE — forme dominante de l'agriculture congolaise ;
--   · un AGRONOME-CONSEIL suivant l'ensemble sans en être propriétaire ;
--   · un OUVRIER travaillant sur toutes les parcelles sans avoir à connaître les
--     marges ;
--   · un TECHNICIEN intervenant sur le matériel, qui n'a besoin de voir ni
--     l'agronomie ni l'économie.
--
-- ============================================================
-- ⚠️  RÈGLE ABSOLUE DE CETTE MIGRATION : RIEN N'EST BLOQUANT.
-- ============================================================
--
-- L'organisation est PUREMENT ADDITIVE. Tout est facultatif, à tous les
-- niveaux :
--
--   plots.farm_id        NULL  → la parcelle appartient à son utilisateur, un
--                                point c'est tout. Comportement d'aujourd'hui,
--                                inchangé.
--   farms.cooperative_id NULL  → exploitation indépendante.
--   aucune ferme créée         → l'application fonctionne exactement comme avant
--                                cette migration.
--
-- Côté applicatif, AccessGuard n'utilise ces tables que pour ÉLARGIR l'accès :
-- le propriétaire direct garde le sien quoi qu'il arrive, et un membre de
-- l'exploitation s'y ajoute. Aucun chemin de code ne peut retirer un accès qui
-- existait avant. Une exploitation mal configurée ne peut donc pas enfermer
-- quelqu'un dehors.
--
-- POURQUOI MAINTENANT malgré tout. Introduire un niveau d'organisation plus tard
-- imposerait de reprendre le cloisonnement de chaque route. C'est ce
-- qu'AccessGuard a été écrit pour éviter : tous les domaines — cultures,
-- boîtiers, relevés, diagnostics, observations, interventions, récoltes —
-- passent par PlotService.require, qui l'appelle. L'élargissement est UNE
-- modification, pas trente.
-- ============================================================

-- ------------------------------------------------------------
-- 1. cooperatives
--
-- Niveau le plus haut, et le plus facultatif : une exploitation indépendante
-- n'en a aucune, et le système n'en exige jamais une.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cooperatives (
    id            BIGINT PRIMARY KEY,
    version       BIGINT       NOT NULL DEFAULT 0,

    name          VARCHAR(150) NOT NULL,
    code          VARCHAR(40),
    location      VARCHAR(255),
    contact_phone VARCHAR(30),

    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP,

    CONSTRAINT chk_cooperatives_status
        CHECK (status IN ('ACTIVE', 'ARCHIVEE'))
);

-- Index partiel : deux coopératives sans code ne doivent pas entrer en
-- collision l'une avec l'autre.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cooperatives_code
    ON cooperatives (code) WHERE code IS NOT NULL;

CREATE SEQUENCE IF NOT EXISTS cooperative_code_seq START WITH 1 INCREMENT BY 1;

-- ------------------------------------------------------------
-- 2. farms — l'exploitation
--
-- Le niveau qui manquait entre l'utilisateur et la parcelle. Rattacher chaque
-- parcelle à un utilisateur ne disait rien de leur appartenance commune : ni
-- qu'un ouvrier travaille sur les cinq, ni qu'un conseiller les suit ensemble.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS farms (
    id             BIGINT PRIMARY KEY,
    version        BIGINT       NOT NULL DEFAULT 0,

    -- NULL = exploitation indépendante. C'est un cas normal, pas une lacune.
    cooperative_id BIGINT REFERENCES cooperatives(id) ON DELETE SET NULL,

    -- Propriétaire de référence. Distinct des membres : celui qui répond de
    -- l'exploitation n'est pas nécessairement celui qui y travaille.
    owner_user_id  BIGINT REFERENCES users(id) ON DELETE SET NULL,

    name           VARCHAR(150) NOT NULL,
    code           VARCHAR(40),
    location       VARCHAR(255),
    contact_phone  VARCHAR(30),

    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,

    CONSTRAINT chk_farms_status
        CHECK (status IN ('ACTIVE', 'ARCHIVEE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_farms_code
    ON farms (code) WHERE code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_farms_cooperative
    ON farms (cooperative_id) WHERE cooperative_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_farms_owner
    ON farms (owner_user_id) WHERE owner_user_id IS NOT NULL;

CREATE SEQUENCE IF NOT EXISTS farm_code_seq START WITH 1 INCREMENT BY 1;

-- ------------------------------------------------------------
-- 3. farm_membership — qui accède à quoi, et à quel titre
--
-- Le rôle n'est pas décoratif : il module l'accès. Un TECHNICIEN doit voir
-- l'état des boîtiers sans voir les marges ; les confondre reviendrait à ouvrir
-- la comptabilité à quiconque vient changer une sonde — ce qui, dans une
-- coopérative où tout le monde se connaît, est un problème social avant d'être
-- technique.
--
-- Rappel : une appartenance AJOUTE un accès. Elle n'en retire jamais au
-- propriétaire direct de la parcelle.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS farm_membership (
    id        BIGINT PRIMARY KEY,
    version   BIGINT      NOT NULL DEFAULT 0,

    farm_id   BIGINT      NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    role      VARCHAR(20) NOT NULL,

    joined_at TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT chk_membership_role
        CHECK (role IN ('PROPRIETAIRE', 'OUVRIER', 'CONSEILLER', 'TECHNICIEN')),

    -- Un rôle par personne et par exploitation : deux rôles simultanés
    -- rendraient indécidable le niveau d'accès applicable, et « lequel
    -- l'emporte ? » n'a pas de bonne réponse.
    CONSTRAINT uq_membership_farm_user UNIQUE (farm_id, user_id)
);

-- « À quelles exploitations cette personne a-t-elle accès ? » — la question que
-- pose AccessGuard à chaque requête.
CREATE INDEX IF NOT EXISTS idx_membership_user
    ON farm_membership (user_id);

-- ------------------------------------------------------------
-- 4. Rattachement des parcelles — facultatif
--
-- NULLABLE, et c'est le point le plus important de cette migration : aucune
-- donnée existante n'est invalidée, et une parcelle sans exploitation continue
-- de fonctionner comme avant, indéfiniment.
-- ------------------------------------------------------------
ALTER TABLE plots ADD COLUMN IF NOT EXISTS farm_id BIGINT;

ALTER TABLE plots ADD CONSTRAINT fk_plot_farm
    FOREIGN KEY (farm_id) REFERENCES farms(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_plots_farm
    ON plots (farm_id) WHERE farm_id IS NOT NULL;
