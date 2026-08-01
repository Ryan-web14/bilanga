-- =====================================================================
-- V32 : retrait du tiret cadratin des textes destinés à l'utilisateur
-- =====================================================================
--
-- POURQUOI
-- --------
-- Le tiret cadratin (U+2014) a été retiré des gabarits de message côté code.
-- Les textes DÉJÀ ÉCRITS en base, eux, le conservent : une recommandation est
-- figée au moment où elle est émise, précisément pour que la justification
-- a posteriori porte sur ce qui a été dit et non sur ce que le système dirait
-- aujourd'hui.
--
-- Sans cette migration, la base de démonstration afficherait donc deux
-- typographies côte à côte : l'ancienne sur les centaines de conseils déjà
-- produits, la nouvelle sur ceux à venir.
--
-- CE QUE CETTE MIGRATION NE TOUCHE PAS
-- ------------------------------------
-- Les champs saisis par un utilisateur : notes d'intervention, notes de
-- récolte, observations de terrain, notes de clôture. Corriger la typographie
-- de quelqu'un d'autre dans ses propres saisies n'est pas du ressort du
-- système.
--
-- REMPLACEMENT
-- ------------
-- ' — ' devient ', ' : c'est la ponctuation qui remplace l'incise dans la
-- quasi-totalité des cas. Un tiret résiduel sans espaces (rare, on le trouve
-- dans un libellé composé) devient un tiret simple.
--
-- Chaque UPDATE porte un WHERE : seules les lignes concernées sont réécrites,
-- ce qui évite de faire remonter la date de modification de toute la table.
-- =====================================================================

-- --- Conseils déjà émis --------------------------------------------------
UPDATE recommendations
   SET content = replace(replace(content, ' — ', ', '), '—', '-')
 WHERE content LIKE '%' || chr(8212) || '%';

-- --- Base de connaissance pilotable --------------------------------------
UPDATE recommendation_arbitration
   SET synthesis = replace(replace(synthesis, ' — ', ', '), '—', '-')
 WHERE synthesis LIKE '%' || chr(8212) || '%';

UPDATE knowledge_rules
   SET proposed_action = replace(replace(proposed_action, ' — ', ', '), '—', '-')
 WHERE proposed_action LIKE '%' || chr(8212) || '%';

UPDATE knowledge_rules
   SET condition_text = replace(replace(condition_text, ' — ', ', '), '—', '-')
 WHERE condition_text LIKE '%' || chr(8212) || '%';

UPDATE correlation_rules
   SET extra_recommendation = replace(replace(extra_recommendation, ' — ', ', '), '—', '-')
 WHERE extra_recommendation LIKE '%' || chr(8212) || '%';

UPDATE disease_knowledge
   SET display_name        = replace(replace(display_name, ' — ', ', '), '—', '-'),
       symptoms            = replace(replace(symptoms, ' — ', ', '), '—', '-'),
       favorable_conditions = replace(replace(favorable_conditions, ' — ', ', '), '—', '-'),
       treatment           = replace(replace(treatment, ' — ', ', '), '—', '-'),
       prevention          = replace(replace(prevention, ' — ', ', '), '—', '-')
 WHERE display_name         LIKE '%' || chr(8212) || '%'
    OR symptoms             LIKE '%' || chr(8212) || '%'
    OR favorable_conditions LIKE '%' || chr(8212) || '%'
    OR treatment            LIKE '%' || chr(8212) || '%'
    OR prevention           LIKE '%' || chr(8212) || '%';

UPDATE disease_risk_condition
   SET label = replace(replace(label, ' — ', ', '), '—', '-')
 WHERE label LIKE '%' || chr(8212) || '%';

UPDATE crop_stage_requirement
   SET label = replace(replace(label, ' — ', ', '), '—', '-')
 WHERE label LIKE '%' || chr(8212) || '%';

-- --- Alertes et notifications --------------------------------------------
-- Le message d'une alerte est figé à la levée, comme un conseil. Il est
-- normalisé ici pour la même raison, et pour la même raison seulement : la
-- cohérence typographique de ce qui s'affiche.
UPDATE alerts
   SET message = replace(replace(message, ' — ', ', '), '—', '-')
 WHERE message LIKE '%' || chr(8212) || '%';

UPDATE notification_outbox
   SET subject = replace(replace(subject, ' — ', ', '), '—', '-')
 WHERE subject LIKE '%' || chr(8212) || '%';

UPDATE notification_outbox
   SET body = replace(replace(body, ' — ', ', '), '—', '-')
 WHERE body LIKE '%' || chr(8212) || '%';
