-- ============================================================
-- V30 — coûts indicatifs sur les règles de connaissance
--
-- LE MANQUE COMBLÉ. La V26 a ajouté `estimated_cost` sur knowledge_rules et
-- disease_knowledge, et câblé la chaîne jusqu'à recommendations.estimated_cost.
-- Aucune valeur n'avait été semée : le champ figurait au contrat frontend et
-- sortait systématiquement à NULL. Un client pouvait raisonnablement en conclure
-- que le backend était cassé.
--
-- ============================================================
-- ⚠️⚠️ CES VALEURS SONT INDICATIVES ET N'ONT ÉTÉ VALIDÉES PAR PERSONNE.
--
-- Elles sont du même ordre que les seuils agronomiques semés par V3, V6, V7 et
-- V10 — dont le commentaire de la V10 dit explicitement qu'ils sont indicatifs.
--
-- MAIS UN PRIX N'EST PAS UN SEUIL, et la différence compte :
--
--     un seuil approximatif oriente une OBSERVATION
--     un prix approximatif oriente une DÉCISION D'ACHAT
--
-- Un exploitant qui renonce à un traitement parce que le système l'annonce à
-- 45 000 XAF alors qu'il en coûte 12 000 perd sa récolte sur une donnée
-- inventée. Le risque n'est pas symétrique.
--
-- CE QU'IL FAUT EN FAIRE :
--   · elles rendent le champ démontrable, et c'est leur seule raison d'être ;
--   · elles doivent être remplacées par des prix relevés auprès d'un
--     fournisseur local AVANT toute exploitation réelle ;
--   · elles se corrigent SANS MIGRATION, par PUT /knowledge/rules/{id} —
--     l'écriture par l'API évince le cache immédiatement.
--
-- Ordre de grandeur retenu : XAF, PAR HECTARE, conditions du Congo-Brazzaville
-- 2026. La règle ignore quelle parcelle la déclenchera ; la multiplication par
-- la surface revient au client, ou à /plots/{id}/economics qui connaît
-- plantedArea.
-- ============================================================

-- ------------------------------------------------------------
-- Règles agronomiques — les sept semées par la V3
--
-- Les identifiants 1 à 7 sont ceux de V3 : ce fichier ne crée aucune règle, il
-- ne fait que valoriser une colonne restée nulle.
-- ------------------------------------------------------------

-- Irrigation d'appoint : eau, carburant de pompe, main-d'œuvre. Sur une
-- parcelle pluviale, IrrigationAdapter reformule le conseil en paillage ou
-- binage — moins coûteux en intrants, davantage en temps. Le coût affiché reste
-- celui de la règle : le système ne sait pas chiffrer une reformulation.
UPDATE knowledge_rules SET estimated_cost = 15000.00 WHERE id = 1;  -- STRESS_HYDRIQUE

-- Drainage : essentiellement de la main-d'œuvre — rigoles, ados. Peu d'intrants.
UPDATE knowledge_rules SET estimated_cost =  8000.00 WHERE id = 2;  -- EXCES_EAU

-- Chaulage : la chaux agricole est bon marché mais s'applique en tonnage. Le
-- conseil recommande un apport FRACTIONNÉ ; ce montant couvre le premier apport.
UPDATE knowledge_rules SET estimated_cost = 45000.00 WHERE id = 3;  -- SOL_ACIDE

-- Matière organique acidifiante : compost ou fumier. Souvent produit sur place,
-- d'où un coût monétaire faible et un coût en temps élevé — que ce champ ne sait
-- pas représenter. À garder en tête en lisant le chiffre.
UPDATE knowledge_rules SET estimated_cost = 25000.00 WHERE id = 4;  -- SOL_ALCALIN

-- Engrais équilibré NPK, apport fractionné.
UPDATE knowledge_rules SET estimated_cost = 55000.00 WHERE id = 5;  -- CARENCES_NUTRITIVES

-- Traitement préventif : fongicide de contact, un passage. Un traitement curatif
-- coûte davantage — c'est précisément l'argument du préventif, et la raison pour
-- laquelle ce conseil est de priorité HAUTE.
UPDATE knowledge_rules SET estimated_cost = 30000.00 WHERE id = 6;  -- RISQUE_MALADIE

-- ⚠️ ZÉRO, ET NON NULL — la distinction est le cœur du contrat de ce champ.
--
-- « Poursuivre le suivi habituel » ne coûte rien en intrants. Le frontend est
-- explicitement prévenu que NULL signifie « non renseigné » et JAMAIS
-- « gratuit », et que zéro est une valeur licite et distincte. Cette ligne est
-- la seule du jeu qui l'illustre.
UPDATE knowledge_rules SET estimated_cost = 0.00 WHERE id = 7;      -- NORMAL

-- ------------------------------------------------------------
-- Maladies — coût du traitement recommandé, par maladie
--
-- Renseigné par CATÉGORIE de traitement plutôt que ligne à ligne : les codes de
-- maladie viennent des classes du modèle de vision et pourraient changer à un
-- réentraînement, alors que le mode de lutte, lui, est stable.
-- ------------------------------------------------------------

-- Maladies fongiques foliaires — fongicide de contact ou systémique, deux à
-- trois passages sur un cycle. C'est le poste le plus lourd des trois.
UPDATE disease_knowledge SET estimated_cost = 40000.00
 WHERE estimated_cost IS NULL
   AND (disease_code ILIKE '%blight%'      -- mildiou, alternariose
     OR disease_code ILIKE '%mold%'        -- cladosporiose
     OR disease_code ILIKE '%spot%'        -- septoriose, taches bactériennes
     OR disease_code ILIKE '%rust%'
     OR disease_code ILIKE '%mosaic%');

-- Viroses et maladies à vecteur — AUCUN traitement curatif n'existe. Le coût est
-- celui de la lutte contre le vecteur et de l'arrachage des plants atteints.
UPDATE disease_knowledge SET estimated_cost = 20000.00
 WHERE estimated_cost IS NULL
   AND (disease_code ILIKE '%virus%'
     OR disease_code ILIKE '%curl%'        -- TYLCV
     OR disease_code ILIKE '%streak%');    -- mosaïque africaine du manioc

-- Acariens et ravageurs — acaricide, un à deux passages.
UPDATE disease_knowledge SET estimated_cost = 18000.00
 WHERE estimated_cost IS NULL
   AND (disease_code ILIKE '%mite%'
     OR disease_code ILIKE '%spider%');

-- Plant sain : rien à traiter. Zéro, et non NULL — même raison qu'à la règle 7.
UPDATE disease_knowledge SET estimated_cost = 0.00
 WHERE disease_code ILIKE '%healthy%';

-- Tout le reste : un traitement générique, plutôt que de laisser NULL. Un champ
-- vide se lit « le système ne sait pas », ce qui est vrai mais inexploitable
-- pour une démonstration.
UPDATE disease_knowledge SET estimated_cost = 25000.00 WHERE estimated_cost IS NULL;

COMMENT ON COLUMN knowledge_rules.estimated_cost IS
    'Coût INDICATIF de l''action, en devise locale et PAR HECTARE. Semé par la '
    'V30 avec des ordres de grandeur NON VALIDÉS, au même titre que les seuils '
    'agronomiques. À remplacer par des prix relevés auprès d''un fournisseur '
    'local avant toute exploitation réelle — un prix approximatif oriente une '
    'décision d''achat, là où un seuil approximatif n''oriente qu''une '
    'observation. Corrigeable sans migration par PUT /knowledge/rules/{id}.';
