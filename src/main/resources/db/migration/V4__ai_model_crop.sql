-- Ajoute la culture au modèle IA pour distinguer manioc / tomate.
ALTER TABLE ai_models ADD COLUMN crop_name VARCHAR(50);

-- Renseigne les modèles existants (ids du seed V3)
UPDATE ai_models SET crop_name = 'manioc' WHERE id = 1;  -- Classifieur Manioc
UPDATE ai_models SET crop_name = 'tomate' WHERE id = 2;  -- Classifieur Tomate
-- id = 3 (tabulaire) reste NULL : il n'est pas lié à une culture unique