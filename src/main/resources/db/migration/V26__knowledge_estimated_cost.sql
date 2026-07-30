-- ============================================================
-- V26 — coût estimé porté par la base de connaissance
--
-- LE DÉFAUT CORRIGÉ (A11). recommendations.estimated_cost existe depuis la V16
-- et RecommendationResponse l'expose au frontend. Mais AUCUNE source ne la
-- renseignait : la colonne était vide, et le champ sortait donc toujours à null.
--
-- Ce n'est pas une simple omission. C'est une promesse faite au frontend et non
-- tenue : Rolle voit le champ dans le contrat, prévoit une colonne « coût
-- estimé » dans son tableau de conseils, et n'obtient jamais de valeur. Il en
-- conclut soit que la donnée n'est pas saisie, soit que le backend est cassé —
-- deux mauvaises réponses. Une capacité annoncée mais inerte est plus coûteuse
-- qu'une capacité absente.
--
-- POURQUOI LE COÛT APPARTIENT À LA RÈGLE, ET NON AU CONSEIL. Le conseil est
-- produit à la volée par le moteur ; la règle, elle, est pilotable par API
-- (/knowledge/rules). Poser le coût sur la règle permet à l'agronome de le
-- renseigner une fois — « un traitement cuivrique coûte environ 32 000 XAF/ha » —
-- et à tous les conseils qui en découlent de le porter, sans code ni
-- redéploiement.
--
-- POURQUOI PAR HECTARE, ET NON EN MONTANT ABSOLU. Un montant absolu n'a de sens
-- que pour une surface donnée. La règle ignore la parcelle qui la déclenchera :
-- un coût à l'hectare est la seule forme qui se transpose. La multiplication par
-- la surface est laissée au calcul économique, qui connaît plantedArea.
--
-- POURQUOI NUMERIC ET NON DOUBLE. C'est de l'argent. Un double introduit des
-- erreurs de représentation qui, additionnées sur une campagne, produisent des
-- totaux qui ne se recoupent pas — et personne ne sait alors lequel croire.
-- NUMERIC(14,2) suit exactement le choix fait en V16 sur recommendations.
-- ============================================================

ALTER TABLE knowledge_rules
    ADD COLUMN IF NOT EXISTS estimated_cost NUMERIC(14, 2);

ALTER TABLE disease_knowledge
    ADD COLUMN IF NOT EXISTS estimated_cost NUMERIC(14, 2);

COMMENT ON COLUMN knowledge_rules.estimated_cost IS
    'Coût indicatif de l''action proposée, en devise locale PAR HECTARE. '
    'Reporté sur recommendations.estimated_cost au moment du diagnostic. '
    'NULL = non renseigné, ce qui n''est pas la même chose que gratuit.';

COMMENT ON COLUMN disease_knowledge.estimated_cost IS
    'Coût indicatif du traitement, en devise locale PAR HECTARE. Même contrat '
    'que knowledge_rules.estimated_cost.';

-- Un coût négatif n'existe pas ; zéro est licite — certaines actions
-- (aération, binage manuel) ne coûtent que du temps, et le distinguer de
-- « non renseigné » est précisément l'intérêt d'accepter zéro.
ALTER TABLE knowledge_rules
    DROP CONSTRAINT IF EXISTS chk_knowledge_rules_cost;
ALTER TABLE knowledge_rules
    ADD CONSTRAINT chk_knowledge_rules_cost
        CHECK (estimated_cost IS NULL OR estimated_cost >= 0);

ALTER TABLE disease_knowledge
    DROP CONSTRAINT IF EXISTS chk_disease_knowledge_cost;
ALTER TABLE disease_knowledge
    ADD CONSTRAINT chk_disease_knowledge_cost
        CHECK (estimated_cost IS NULL OR estimated_cost >= 0);

-- ============================================================
-- Aucune valeur n'est semée.
--
-- ⚠️ C'est délibéré, et c'est le point le plus important de cette migration.
-- Les seuils agronomiques semés par V3, V6, V7 et V10 sont déjà « indicatifs et
-- à valider » (A12). Y ajouter des PRIX inventés serait franchir une ligne : un
-- seuil approximatif orient l'observation, un prix approximatif oriente une
-- DÉCISION D'ACHAT. Le renseigner sans source serait présenter une supposition
-- comme un chiffrage.
--
-- Le champ reste donc null jusqu'à ce qu'un agronome ou un fournisseur local
-- fournisse des valeurs sourcées. La différence avec l'état antérieur est que
-- la donnée est désormais SAISISSABLE — par PUT /knowledge/rules/{id} — au lieu
-- d'être impossible à renseigner.
-- ============================================================
