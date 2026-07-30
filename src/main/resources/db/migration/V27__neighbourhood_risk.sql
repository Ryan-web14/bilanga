-- ============================================================
-- V27 — risque de voisinage : le huitième moteur
--
-- CE QUE CETTE MIGRATION FAIT, ET POURQUOI ELLE EST INDISPENSABLE
--
-- Elle étend chk_recommendations_type à 'VOISINAGE'. C'est LE piège du projet,
-- et il a déjà failli coûter cher au rang 6 : la contrainte de la V11 énumérait
-- les moteurs d'alors, et la première recommandation météo aurait fait échouer
-- son insertion. Or cette insertion survient AU CŒUR DU DIAGNOSTIC — l'échec
-- aurait donc fait perdre le diagnostic entier, pour un type de conseil non
-- déclaré. La V20 avait dû reprendre la contrainte ; la V27 fait de même.
--
-- POURQUOI UN TYPE DISTINCT ET NON 'RISQUE'
--
-- La tentation était de réutiliser 'RISQUE' pour éviter cette migration. Elle a
-- été écartée : les deux ne se lisent pas de la même façon, et l'exploitant doit
-- pouvoir les distinguer.
--
--   · 'RISQUE'    — VOS mesures réunissent les conditions d'apparition. C'est
--                   observable sur votre parcelle, vérifiable par vous.
--   · 'VOISINAGE' — une maladie a été DÉTECTÉE CHEZ UN VOISIN. Rien n'est
--                   observable chez vous, et c'est justement l'intérêt : le
--                   conseil est préventif.
--
-- Les confondre produirait un conseil incompréhensible — « conditions favorables
-- au mildiou » alors que les mesures locales ne le disent pas. L'exploitant
-- chercherait l'erreur dans ses sondes.
--
-- EN REVANCHE LA CATÉGORIE EST RÉUTILISÉE ('RISQUE_MALADIE'), et c'est
-- délibéré : c'est elle que ConflictArbitrator et la déduplication regardent. Un
-- risque local et un risque de voisinage portant sur la même maladie relèvent du
-- même domaine d'action, et doivent donc pouvoir être réconciliés plutôt que
-- empilés.
-- ============================================================

ALTER TABLE recommendations DROP CONSTRAINT IF EXISTS chk_recommendations_type;

ALTER TABLE recommendations ADD CONSTRAINT chk_recommendations_type
    CHECK (recommendation_type IS NULL OR recommendation_type IN
           ('BASE', 'AGRONOMIQUE', 'RISQUE', 'TENDANCE', 'CORRELATION',
            'ARBITRAGE', 'METEO', 'VOISINAGE'));

-- ------------------------------------------------------------
-- Index de recherche géographique
--
-- idx_plots_coordinates (V16) porte déjà (latitude, longitude) avec un index
-- PARTIEL sur les parcelles géolocalisées. Il suffit à la requête de voisinage,
-- qui commence par un encadrement rectangulaire des coordonnées.
--
-- Aucun index supplémentaire n'est créé, et surtout PAS PostGIS. Pour un rayon
-- de deux kilomètres, la formule de Haversine appliquée à un pré-filtre
-- rectangulaire suffit : ajouter une extension géospatiale entière pour cela
-- serait exactement le genre de dépendance surdimensionnée dont le lot 5 vient
-- de débarrasser le pom.xml.
--
-- Le pré-filtre rectangulaire est ce qui rend l'index utile : sans lui, une
-- formule de distance dans le WHERE interdirait au planificateur de s'en servir,
-- et chaque diagnostic balaierait toute la table des parcelles.
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- Index sur la recherche des diagnostics récents des voisins
--
-- La requête filtre sur plot_id ∈ (voisins), diagnosed_at récent, et result
-- différent de NORMAL. idx_diagnostics_plot_date (V5) couvre les deux premiers
-- critères ; le troisième est sélectif dans le bon sens — la plupart des
-- diagnostics SONT normaux — ce qui en fait un bon candidat pour un index
-- partiel.
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_diagnostics_abnormal_recent
    ON diagnostics (diagnosed_at DESC, plot_id)
    WHERE result IS NOT NULL AND result <> 'NORMAL';

COMMENT ON INDEX idx_diagnostics_abnormal_recent IS
    'Sert NeighbourhoodEngine : diagnostics anormaux récents des parcelles '
    'voisines. Partiel, car la majorité des diagnostics sont NORMAL et '
    'n''ont aucun intérêt pour la propagation.';
