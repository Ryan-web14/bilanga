-- ============================================================
-- V28 — clôture riche des cycles de culture, et journal de révisions
--
-- LE MANQUE COMBLÉ
--
-- `crops` est une entité plate : ni updated_at, ni date de fin réelle, ni motif,
-- ni journal. CropServiceImpl.delete() se contente de passer status → TERMINEE.
-- On ne sait donc ni QUAND la campagne s'est réellement achevée, ni POURQUOI
-- (récolte normale ? perte climatique ? parcelle retournée ?), ni CE QU'ELLE A
-- RAPPORTÉ au moment où on l'a close.
--
-- Pire : CropServiceImpl.update() écrase inconditionnellement les champs omis
-- d'un PUT partiel, sans que rien ne le consigne. Il était impossible de
-- répondre à « qu'a donné la campagne de l'an dernier, et qui a changé la
-- surface plantée en cours de route ? ».
--
-- ============================================================
-- ⚠️ AMENDEMENT AU CONTRAT « TOUT RECALCULÉ, RIEN STOCKÉ »
--
-- L'en-tête de V21__harvest.sql et les javadocs de MarginCalculator posent que
-- les totaux économiques se recalculent toujours et ne sont jamais stockés, au
-- motif — juste — qu'« un total mis en cache diverge dès la première correction
-- de saisie, et personne ne sait plus lequel des deux chiffres croire ».
--
-- economics_snapshot introduit un total stocké. Ce n'est PAS un abandon du
-- contrat, et il faut que la raison figure ici puisque V21 est intouchable :
--
--   1. il est écrit UNE SEULE FOIS, à la clôture, et JAMAIS rafraîchi ;
--      aucune route ne permet de le recalculer ;
--   2. il est étiqueté « bilan ARRÊTÉ à la clôture », jamais « le bilan », et
--      porte sa date et son auteur (closed_at, closed_by) ;
--   3. la route vivante subsiste : /harvests/economics continue de tout
--      recalculer, et GET /crops/{id}/closure rend LES DEUX CÔTE À CÔTE avec
--      leur divergence expliquée en français.
--
-- Personne ne se demande donc lequel croire : les deux sont là, datés, et
-- l'écart DEVIENT le signal d'audit. « Le bilan arrêté le 14/03 indiquait
-- 412 000 XAF. Recalculé aujourd'hui : 388 500 XAF. Deux récoltes ont été
-- saisies depuis la clôture. » La tension du contrat devient une information.
--
-- À noter : harvestRepository.delete() est une suppression RÉELLE (assumée,
-- javadoc explicite). Une récolte supprimée après clôture rend le bilan figé
-- faux — et c'est exactement ce que la ligne de divergence rend visible.
-- ============================================================

-- ------------------------------------------------------------
-- Clôture
--
-- actual_end_date ≠ expected_harvest_date : l'une est un objectif calculé par
-- GrowthStageResolver, l'autre un constat. Les confondre ferait passer une
-- prévision pour un fait.
-- ------------------------------------------------------------
ALTER TABLE crops ADD COLUMN IF NOT EXISTS actual_end_date    DATE;
ALTER TABLE crops ADD COLUMN IF NOT EXISTS closure_reason     VARCHAR(30);
ALTER TABLE crops ADD COLUMN IF NOT EXISTS closure_note       TEXT;
ALTER TABLE crops ADD COLUMN IF NOT EXISTS closed_at          TIMESTAMP;
ALTER TABLE crops ADD COLUMN IF NOT EXISTS closed_by          BIGINT
    REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE crops ADD COLUMN IF NOT EXISTS economics_snapshot JSONB;
ALTER TABLE crops ADD COLUMN IF NOT EXISTS updated_at         TIMESTAMP;

ALTER TABLE crops DROP CONSTRAINT IF EXISTS chk_crops_closure_reason;
ALTER TABLE crops ADD CONSTRAINT chk_crops_closure_reason
    CHECK (closure_reason IS NULL OR closure_reason IN
           ('RECOLTE_NORMALE',      -- terme atteint, récolte faite
            'RECOLTE_ANTICIPEE',    -- récoltée avant terme (prix, météo, maladie)
            'PERTE_MALADIE',
            'PERTE_CLIMATIQUE',
            'PERTE_RAVAGEURS',
            'ABANDON',              -- non récoltée, laissée en place
            'RETOURNEE',            -- sol retravaillé, culture détruite
            'ERREUR_DE_SAISIE'));   -- la campagne n'a jamais existé

-- ⚠️ AUCUNE contrainte « status = 'TERMINEE' ⇒ actual_end_date NOT NULL ».
--
-- Les lignes déjà fermées par l'ancien delete() n'ont pas de date de fin, et
-- elles n'en auront jamais : l'information n'a pas été perdue, elle n'a jamais
-- été demandée. Ce NULL EST donc l'information — « close avant que le système
-- ne demande un motif ».
--
-- Une contrainte ici obligerait à inventer des dates par backfill, ce qui
-- fabriquerait des faits. Et avec NOT VALID, toute mise à jour future d'une de
-- ces lignes échouerait — dans un dépôt où une migration appliquée ne se
-- corrige plus, c'est la combinaison la plus dangereuse possible.

-- ------------------------------------------------------------
-- Journal de révisions
--
-- Pourquoi une table dédiée plutôt qu'audit_log : audit_log est indexé par
-- module/action/acteur et sert la supervision transverse. Le journal d'un cycle
-- se lit PAR CYCLE et PAR PARCELLE, du plus récent au plus ancien — deux accès
-- que audit_log n'indexe pas.
--
-- ⚠️ Contrairement à setting_audit_logs (V1), qui est un journal structurel que
-- RIEN n'alimente depuis l'origine, celui-ci est écrit dès sa création par
-- CropJournalWriter. Un second journal mort serait la même erreur, en pire.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crop_journal (
    id               BIGINT       PRIMARY KEY,

    crop_id          BIGINT       NOT NULL REFERENCES crops(id) ON DELETE CASCADE,

    -- Dénormalisé : permet « le journal de cette parcelle, toutes campagnes
    -- confondues » sans jointure, et survit à la lecture d'un cycle archivé.
    plot_id          BIGINT       NOT NULL REFERENCES plots(id),

    -- crops.version LU AVANT l'écriture. Hibernate incrémente au flush : la
    -- valeur d'après n'est pas disponible sans flush() explicite. La colonne est
    -- nommée pour ce qu'elle est, et non « version » tout court.
    crop_version     BIGINT,

    event_type       VARCHAR(30)  NOT NULL,

    -- Sortie de AuditDiffUtil.diff : { champ: { before, after } }.
    -- ⚠️ Les identifiants Snowflake y sortent en CHAÎNES, comme partout dans
    -- l'API (JacksonConfig enregistre ToStringSerializer pour Long/long).
    -- N'écrivez jamais une requête jsonb qui les attendrait en nombres.
    changes          JSONB        NOT NULL DEFAULT '{}'::jsonb,

    reason           VARCHAR(500),

    changed_by       BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    -- Conservé en clair : l'utilisateur peut être supprimé (ON DELETE SET NULL),
    -- et un journal qui perd le nom de son auteur perd sa raison d'être.
    changed_by_email VARCHAR(150),

    changed_at       TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_crop_journal_event CHECK (event_type IN
        ('CREATION',
         'MODIFICATION',
         'STADE_RECALCULE',   -- GrowthStageResolver a réaligné le stade
         'CLOTURE',
         'CLONAGE'))
);

-- Le journal d'un cycle, du plus récent au plus ancien.
CREATE INDEX IF NOT EXISTS idx_crop_journal_crop ON crop_journal (crop_id, changed_at DESC);
-- Le journal d'une parcelle, toutes campagnes confondues.
CREATE INDEX IF NOT EXISTS idx_crop_journal_plot ON crop_journal (plot_id, changed_at DESC);

-- ON DELETE CASCADE assumé : un journal qui survivrait à son sujet serait
-- orphelin, et `crops` n'est de toute façon jamais supprimée durement — delete()
-- est un archivage logique. RESTRICT bloquerait une suppression légitime.

-- ------------------------------------------------------------
-- chk_recommendations_type — le piège, inversé
--
-- La contrainte de V11 énumérait les moteurs d'alors. V20 a dû la reprendre pour
-- 'METEO', V27 pour 'VOISINAGE'. À chaque fois le même risque : un type non
-- déclaré fait échouer l'insertion AU CŒUR DU DIAGNOSTIC, et fait donc perdre le
-- diagnostic ENTIER — pas seulement le conseil.
--
-- On renverse donc le coût. Une valeur PERMISE ET INUTILISÉE ne coûte rien ; une
-- valeur ÉMISE ET NON PERMISE coûte un diagnostic. 'ITINERAIRE' est ajouté
-- maintenant, alors que RIEN ne l'émet encore — le lot « itinéraire technique »
-- (V29) est le prochain candidat naturel à en produire (« l'opération prévue du
-- 12 mai n'a pas eu lieu »).
--
-- ⚠️ Ne pas en déduire qu'on peut réutiliser un type existant pour esquiver une
-- migration : V27 a tranché l'inverse — types distincts, catégorie partagée,
-- afin que la déduplication et l'arbitrage continuent de fonctionner.
-- ------------------------------------------------------------
ALTER TABLE recommendations DROP CONSTRAINT IF EXISTS chk_recommendations_type;
ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_type
    CHECK (recommendation_type IS NULL OR recommendation_type IN
           ('BASE', 'AGRONOMIQUE', 'RISQUE', 'TENDANCE', 'CORRELATION',
            'ARBITRAGE', 'METEO', 'VOISINAGE', 'ITINERAIRE'));

COMMENT ON COLUMN crops.actual_end_date IS
    'Date de fin RÉELLE de la campagne, à distinguer de expected_harvest_date qui '
    'est un objectif calculé. NULL sur les cycles clos avant la V28 : '
    'l''information n''a jamais été demandée, elle n''est pas perdue.';

COMMENT ON COLUMN crops.economics_snapshot IS
    'Bilan économique ARRÊTÉ à la clôture, écrit une seule fois et jamais '
    'rafraîchi. Porte "scope" et "zoneId" en prévision du zonage de parcelle : '
    'sans eux, des bilans de parcelle et de zone seraient indiscernables, et '
    'aucune migration ne pourrait réparer une information jamais écrite.';
