-- ============================================================
-- V25 — index composite pour l'analyse de santé des sondes
--
-- Le constat qui la motive. « Aucun index sur sensor_readings(device_id, …) »
-- était FAUX : idx_readings_device existe depuis la V5. Ce qui manque est le
-- composite, et la nuance compte.
--
-- SensorHealthAnalyzer est appelé à CHAQUE ingestion, et sa requête est
--   findByDevice_IdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc
-- soit : filtrer sur device_id, borner sur recorded_at, trier par recorded_at
-- décroissant, prendre les N premiers.
--
-- Avec le seul index sur (device_id), PostgreSQL sait trouver les lignes du
-- boîtier — puis doit les RAPPORTER TOUTES pour les trier. Sur un boîtier qui
-- émet toutes les cinq minutes, cela fait quelques milliers de lignes lues et
-- triées à chaque relevé reçu, pour n'en garder que six. Le coût croît avec
-- l'historique, ce qui est le pire profil : le système ralentit à mesure qu'il
-- sert.
--
-- Avec le composite (device_id, recorded_at DESC), le tri est déjà fait par
-- l'index : PostgreSQL descend directement aux premières lignes et s'arrête.
-- Le coût devient indépendant de la taille de l'historique.
--
-- L'ordre DESC dans l'index n'est pas cosmétique : il fait correspondre le sens
-- de parcours à celui du ORDER BY, ce qui évite un Backward Index Scan.
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_readings_device_date
    ON sensor_readings (device_id, recorded_at DESC);

COMMENT ON INDEX idx_readings_device_date IS
    'Sert findPeerReadings et la fenêtre glissante de SensorHealthAnalyzer, '
    'appelés à chaque ingestion. Le composite évite de trier tout '
    'l''historique du boîtier pour n''en garder que les derniers relevés.';

-- L'index simple de la V5 devient redondant : (device_id, recorded_at) le
-- couvre pour toute requête qui ne filtre que sur device_id — un index
-- composite sert aussi de préfixe. Il n'est PAS supprimé ici : une suppression
-- d'index est irréversible sans reconstruction (coûteuse sur une grosse table),
-- et le gain d'espace ne justifie pas le risque. À traiter séparément si la
-- volumétrie l'impose.
