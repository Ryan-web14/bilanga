-- ============================================================
-- V2 — Données de référence de la base de connaissance
-- Codes maladie NORMALISÉS (sans préfixe de culture) : le
-- backend retire le préfixe "Xxx___" avant de chercher ici.
-- IDs explicites (générateur custom non utilisé pour le seed).
-- ============================================================

-- ============================================================
-- Les 3 modèles IA (traçabilité des diagnostics)
-- ============================================================
INSERT INTO ai_models (id, name, model_type, version, precision_score, trained_at, status) VALUES
                                                                                               (1, 'Classifieur Manioc EfficientNet-B0', 'VISION',  '1.0', 0.64, now(), 'ACTIVE'),
                                                                                               (2, 'Classifieur Tomate EfficientNet-B0', 'VISION',  '1.0', 0.98, now(), 'ACTIVE'),
                                                                                               (3, 'Diagnostic Sol RandomForest',        'TABULAR', '1.0', 0.82, now(), 'ACTIVE');

-- ============================================================
-- Seuils agronomiques par culture (crop_requirement)
-- ============================================================
INSERT INTO crop_requirement (id, crop_name, ph_min, ph_max, hum_sol_min, hum_sol_max,
                              temp_min, temp_max, azote_min, phosphore_min, potassium_min, tolerance_secheresse) VALUES
                                                                                                                     (1, 'tomate', 6.0, 6.8, 60, 80, 20, 30, 35, 18, 35, 0.0),
                                                                                                                     (2, 'manioc', 5.5, 6.5, 40, 70, 22, 32, 20, 12, 25, 0.6);

-- ============================================================
-- Règles mesures -> recommandation (knowledge_rules)
-- ============================================================
INSERT INTO knowledge_rules (id, category, crop_name, condition_text, proposed_action, priority, validated) VALUES
                                                                                                                (1, 'STRESS_HYDRIQUE', '*', 'humidite_sol sous le seuil bas de la culture',
                                                                                                                 'Irriguer la parcelle rapidement, de préférence tôt le matin ou en soirée pour limiter l''évaporation. Surveiller l''humidité du sol les jours suivants.', 'HAUTE', TRUE),

                                                                                                                (2, 'EXCES_EAU', '*', 'humidite_sol au-dessus du seuil haut de la culture',
                                                                                                                 'Réduire ou suspendre l''irrigation. Améliorer le drainage de la parcelle. Surveiller l''apparition de maladies racinaires favorisées par l''excès d''eau.', 'HAUTE', TRUE),

                                                                                                                (3, 'SOL_ACIDE', '*', 'pH inférieur à la plage optimale',
                                                                                                                 'Procéder à un chaulage (apport de chaux) pour remonter le pH. Fractionner l''apport et re-tester le sol après quelques semaines.', 'MOYENNE', TRUE),

                                                                                                                (4, 'SOL_ALCALIN', '*', 'pH supérieur à la plage optimale',
                                                                                                                 'Apporter de la matière organique acidifiante (compost, fumier). Éviter les amendements calcaires. Re-tester le pH régulièrement.', 'MOYENNE', TRUE),

                                                                                                                (5, 'CARENCES_NUTRITIVES', '*', 'un ou plusieurs nutriments (N, P, K) sous le seuil',
                                                                                                                 'Apporter un engrais équilibré adapté à la carence identifiée. Privilégier un apport fractionné pour une meilleure assimilation.', 'MOYENNE', TRUE),

                                                                                                                (6, 'RISQUE_MALADIE', '*', 'humidite_air élevée et température favorable aux pathogènes',
                                                                                                                 'Renforcer la surveillance sanitaire de la parcelle. Améliorer l''aération, éviter l''arrosage du feuillage, et envisager un traitement préventif.', 'HAUTE', TRUE),

                                                                                                                (7, 'NORMAL', '*', 'aucune anomalie détectée',
                                                                                                                 'Conditions satisfaisantes. Poursuivre le suivi habituel de la parcelle.', 'BASSE', TRUE);

-- ============================================================
-- Connaissances maladies (disease_knowledge)
-- disease_code = classe du modèle APRÈS normalisation.
-- ============================================================
-- --- MANIOC ---
INSERT INTO disease_knowledge (id, crop_name, disease_code, display_name, symptoms,
                               favorable_conditions, treatment, prevention, priority) VALUES
                                                                                          (1, 'manioc', 'bacterial_blight', 'Bactériose du manioc',
                                                                                           'Taches angulaires brunes sur les feuilles, flétrissement, exsudats gommeux sur les tiges.',
                                                                                           'Forte humidité, températures chaudes, blessures des plants.',
                                                                                           'Retirer et détruire les plants gravement atteints. Utiliser des boutures saines certifiées. Désinfecter les outils de coupe.',
                                                                                           'Planter des variétés résistantes, éviter les blessures, assurer une rotation des cultures.', 'HAUTE'),

                                                                                          (2, 'manioc', 'brown_streak_disease', 'Striure brune du manioc',
                                                                                           'Stries brunes sur les tiges, nécroses brunes dans les racines tubéreuses, jaunissement foliaire.',
                                                                                           'Présence de l''aleurode vecteur, cultures continues sans rotation.',
                                                                                           'Arracher et détruire les plants infectés. Utiliser du matériel de plantation sain. Lutter contre les aleurodes vecteurs.',
                                                                                           'Boutures saines, variétés tolérantes, contrôle des vecteurs.', 'HAUTE'),

                                                                                          (3, 'manioc', 'green_mottle', 'Marbrure verte du manioc',
                                                                                           'Mosaïque vert clair/vert foncé sur les jeunes feuilles, légère déformation foliaire.',
                                                                                           'Transmission par boutures infectées et vecteurs.',
                                                                                           'Éliminer les plants atteints. Employer des boutures indemnes. Surveiller la propagation.',
                                                                                           'Matériel végétal sain, surveillance régulière.', 'MOYENNE'),

                                                                                          (4, 'manioc', 'mosaic_disease', 'Mosaïque du manioc',
                                                                                           'Mosaïque jaune/vert marquée, déformation et réduction de la taille des feuilles, retard de croissance.',
                                                                                           'Aleurodes vecteurs, boutures contaminées.',
                                                                                           'Isoler et détruire les plants malades. Replanter à partir de boutures saines. Lutter contre les aleurodes.',
                                                                                           'Variétés résistantes, boutures certifiées, élimination précoce des foyers.', 'HAUTE'),

                                                                                          (5, 'manioc', 'healthy', 'Manioc sain', 'Feuillage vert et vigoureux, absence de symptômes.',
                                                                                           '-', 'Aucun traitement nécessaire. Poursuivre les bonnes pratiques culturales.',
                                                                                           'Maintenir la surveillance et les bonnes pratiques.', 'BASSE');

-- --- TOMATE ---
INSERT INTO disease_knowledge (id, crop_name, disease_code, display_name, symptoms,
                               favorable_conditions, treatment, prevention, priority) VALUES
                                                                                          (6, 'tomate', 'Early_blight', 'Alternariose (brûlure précoce)',
                                                                                           'Taches brunes concentriques (cibles) sur les feuilles âgées, jaunissement, défoliation.',
                                                                                           'Temps chaud et humide, alternance humidité/sécheresse.',
                                                                                           'Retirer les feuilles atteintes. Appliquer un fongicide adapté. Éviter l''arrosage du feuillage.',
                                                                                           'Rotation des cultures, paillage, espacement suffisant des plants.', 'HAUTE'),

                                                                                          (7, 'tomate', 'Late_blight', 'Mildiou de la tomate',
                                                                                           'Taches huileuses brun-noir sur feuilles et tiges, feutrage blanc au revers, pourriture des fruits.',
                                                                                           'Forte humidité de l''air, températures modérées, feuillage mouillé.',
                                                                                           'Retirer et détruire les parties atteintes. Traiter rapidement au fongicide. Améliorer l''aération.',
                                                                                           'Variétés résistantes, éviter l''arrosage foliaire, espacer les plants.', 'HAUTE'),

                                                                                          (8, 'tomate', 'Leaf_Mold', 'Cladosporiose (moisissure des feuilles)',
                                                                                           'Taches jaunes sur la face supérieure, feutrage olive/brun au revers des feuilles.',
                                                                                           'Humidité élevée, mauvaise ventilation (sous serre notamment).',
                                                                                           'Améliorer l''aération et réduire l''humidité. Retirer les feuilles atteintes. Fongicide si nécessaire.',
                                                                                           'Ventilation, espacement, contrôle de l''humidité.', 'MOYENNE'),

                                                                                          (9, 'tomate', 'Tomato_Yellow_Leaf_Curl_Virus', 'Virus des feuilles jaunes en cuillère (TYLCV)',
                                                                                           'Enroulement et jaunissement des feuilles, rabougrissement, chute des fleurs.',
                                                                                           'Présence d''aleurodes vecteurs, climat chaud.',
                                                                                           'Arracher les plants atteints. Lutter contre les aleurodes. Pas de traitement curatif du virus.',
                                                                                           'Variétés résistantes, filets anti-insectes, contrôle des aleurodes.', 'HAUTE'),

                                                                                          (10, 'tomate', 'Tomato_mosaic_virus', 'Virus de la mosaïque de la tomate (ToMV)',
                                                                                           'Mosaïque vert clair/foncé, déformation des feuilles, réduction de croissance.',
                                                                                           'Transmission mécanique (mains, outils), semences contaminées.',
                                                                                           'Éliminer les plants infectés. Désinfecter les outils et se laver les mains. Pas de traitement curatif.',
                                                                                           'Semences saines, hygiène des outils, variétés résistantes.', 'HAUTE'),

                                                                                          (11, 'tomate', 'healthy', 'Tomate saine', 'Feuillage vert et sain, absence de symptômes.',
                                                                                           '-', 'Aucun traitement nécessaire. Poursuivre les bonnes pratiques culturales.',
                                                                                           'Maintenir la surveillance et les bonnes pratiques.', 'BASSE');

-- ============================================================
-- Règles de corrélation maladie <-> mesures (raisonnement croisé)
-- disease_code également normalisé.
-- ============================================================
INSERT INTO correlation_rules (id, crop_name, disease_code, measure_field, operator, threshold, extra_recommendation, priority) VALUES
                                                                                                                                    (1, 'tomate', 'Late_blight', 'humidite_air', '>', 80,
                                                                                                                                     'Les conditions actuelles très humides favorisent fortement la progression du mildiou. Améliorez l''aération entre les plants et évitez absolument d''arroser le feuillage.', 'HAUTE'),

                                                                                                                                    (2, 'tomate', 'Leaf_Mold', 'humidite_air', '>', 75,
                                                                                                                                     'L''humidité élevée mesurée aggrave le risque de cladosporiose. Réduisez l''humidité ambiante et améliorez la ventilation.', 'HAUTE'),

                                                                                                                                    (3, '*', '*', 'humidite_air', '>', 85,
                                                                                                                                     'L''humidité de l''air est très élevée : conditions générales favorables au développement des maladies fongiques. Renforcez la surveillance sanitaire.', 'MOYENNE'),

                                                                                                                                    (4, '*', '*', 'potassium', '<', 25,
                                                                                                                                     'La carence en potassium détectée affaiblit les défenses naturelles de la plante face à la maladie. Un apport de potassium est recommandé en complément du traitement.', 'MOYENNE'),

                                                                                                                                    (5, 'manioc', 'mosaic_disease', 'temperature', '>', 30,
                                                                                                                                     'Les températures élevées accélèrent l''activité des aleurodes vecteurs de la mosaïque. Intensifiez la lutte contre les vecteurs.', 'HAUTE');