-- ============================================================
-- V23 — Complément de la V22
--
-- POURQUOI CETTE MIGRATION EXISTE. La V22 a été écrite en trois passes au cours
-- d'une même séance (coopérative → exploitation seule → organisation complète),
-- et la base de développement a reçu la PREMIÈRE version. Le fichier local a
-- ensuite changé, d'où le refus de Flyway : « checksum mismatch for version 22 ».
--
-- C'est exactement la règle « ne jamais éditer une migration appliquée », et
-- elle a été enfreinte. La réparation se fait ici, pas en retouchant la V22 —
-- une migration appliquée ailleurs ne doit plus bouger.
--
-- CE QUI MANQUAIT, par rapport à V22__organization.sql :
--   · farms.contact_phone        → ddl-auto:validate échouait sur Farm.contactPhone
--   · cooperative_code_seq       → OrganizationServiceImpl.nextCooperativeCode()
--   · farm_code_seq              → OrganizationServiceImpl.nextFarmCode()
--   · idx_farms_owner            → confort de lecture
--
-- ⚠️ TOUT EST CONDITIONNEL. Sur une base NEUVE, la V22 crée déjà ces objets et
-- cette migration ne fait rien. Sur la base de développement, elle comble le
-- trou. Les deux chemins convergent vers le même schéma — c'est la condition
-- pour que ce rattrapage ne devienne pas lui-même une source de divergence.
-- ============================================================

-- ------------------------------------------------------------
-- 1. farms.contact_phone
--
-- Le seul écart qui empêchait le démarrage : Hibernate valide la présence de
-- chaque colonne mappée, et Farm.contactPhone n'avait rien en face.
-- ------------------------------------------------------------
ALTER TABLE farms ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(30);

-- ------------------------------------------------------------
-- 2. Séquences des références lisibles
--
-- COOP-2026-000003 et EXPL-2026-000014, sur le modèle de plot_code_seq (V16).
-- Une séquence de base et non un comptage applicatif : deux créations
-- simultanées liraient le même total et produiraient le même code, que l'index
-- unique rejetterait ensuite.
--
-- Sans elles, nextval() lève une erreur SQL et la création échoue — non pas au
-- démarrage, mais à la première coopérative créée, ce qui est plus difficile à
-- rattacher à sa cause.
-- ------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS cooperative_code_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS farm_code_seq        START WITH 1 INCREMENT BY 1;

-- ------------------------------------------------------------
-- 3. Index sur le propriétaire de référence
--
-- « Quelles exploitations cette personne possède-t-elle ? » — le filtre
-- ownerId de la recherche d'exploitations.
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_farms_owner
    ON farms (owner_user_id) WHERE owner_user_id IS NOT NULL;
