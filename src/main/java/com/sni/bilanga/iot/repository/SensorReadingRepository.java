package com.sni.bilanga.iot.repository;


import com.sni.bilanga.iot.model.SensorReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    List<SensorReading> findByPlot_IdOrderByRecordedAtDesc(Long plotId);

    /** Fenêtre d'observation bornée, pour l'analyse de tendance. */
    List<SensorReading> findByPlot_IdOrderByRecordedAtDesc(Long plotId, Pageable pageable);

    /** Dernier relevé de la parcelle : base de la corrélation automatique. */
    Optional<SensorReading> findFirstByPlot_IdOrderByRecordedAtDesc(Long plotId);

    /**
     * Dernier relevé <strong>à ou avant</strong> un instant donné.
     *
     * <p>Sert la vue « diagnostic à l'instant T » : l'utilisateur désigne un moment
     * sur la courbe d'historique, et il faut retrouver ce que les sondes disaient
     * alors. {@link #findFirstByPlot_IdOrderByRecordedAtDesc} n'en est que le cas
     * dégénéré où l'instant est « maintenant ».
     *
     * <p><strong>Aucune migration n'a été nécessaire</strong> :
     * {@code idx_readings_plot_date (plot_id, recorded_at DESC)} existe depuis la V5
     * et convient exactement — PostgreSQL descend l'index et s'arrête à la première
     * ligne.
     *
     * <p>Le choix du relevé « le plus proche » se tranche <em>en Java</em>, en
     * comparant les deux candidats de part et d'autre. Un {@code order by abs(...)}
     * en SQL serait plus court à écrire mais non-sargable, donc incapable d'utiliser
     * l'index — sur une table de séries temporelles, c'est la différence entre lire
     * une ligne et balayer l'historique entier.
     */
    Optional<SensorReading> findFirstByPlot_IdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
            Long plotId, Instant at);

    /**
     * Premier relevé <strong>à ou après</strong> un instant donné.
     *
     * <p>Le pendant de la méthode précédente. Les deux sont nécessaires : un instant
     * choisi dans un trou de transmission — coupure réseau, boîtier à plat — n'a pas
     * de relevé antérieur proche, et le suivant est alors la meilleure réponse. Ne
     * garder que le passé rendrait « aucune donnée » là où la donnée existe, dix
     * minutes plus tard.
     */
    Optional<SensorReading> findFirstByPlot_IdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
            Long plotId, Instant at);

    /**
     * Fenêtre récente d'un boîtier donné — l'unité d'analyse de la santé des
     * sondes. Raisonner à l'échelle de la parcelle mêlerait les mesures de
     * plusieurs boîtiers et masquerait précisément ce qu'on cherche : celui qui
     * s'écarte des autres.
     */
    List<SensorReading> findByDevice_IdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            Long deviceId, Instant since, Pageable pageable);

    /**
     * Relevés récents des <em>autres</em> boîtiers de la parcelle.
     *
     * Ce sont les témoins : sans point de comparaison, une dérive lente est
     * indiscernable d'une évolution réelle du sol.
     */
    @Query("""
           select r from SensorReading r
           where r.plot.id = :plotId
             and r.device.id is not null
             and r.device.id <> :excludedDeviceId
             and r.recordedAt >= :since
           order by r.recordedAt desc
           """)
    List<SensorReading> findPeerReadings(@Param("plotId") Long plotId,
                                         @Param("excludedDeviceId") Long excludedDeviceId,
                                         @Param("since") Instant since,
                                         Pageable pageable);

    /**
     * Recherche paginée sur la série temporelle.
     *
     * Une parcelle instrumentée produit des milliers de relevés : les ramener
     * tous, comme le faisait {@code findByPlot_Id...}, n'était tenable qu'en
     * démonstration. Les bornes temporelles et le filtre d'anomalie évitent en
     * outre de rapatrier l'historique pour le trier côté client.
     */
    @Query("""
           select r from SensorReading r
           where (:plotId is null or r.plot.id = :plotId)
             and (:deviceId is null or r.device.id = :deviceId)
             and r.recordedAt >= :from
             and r.recordedAt <= :to
             and (:anomalyOnly = false or r.anomalyDetected = true)
             and (:quality is null or upper(r.quality) = :quality)
           """)
    Page<SensorReading> search(@Param("plotId") Long plotId,
                               @Param("deviceId") Long deviceId,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               @Param("anomalyOnly") boolean anomalyOnly,
                               @Param("quality") String quality,
                               Pageable pageable);

    /**
     * Série agrégée par intervalle : min, moyenne et max de chaque mesure.
     *
     * <p>Requête native : {@code date_trunc} est propre à PostgreSQL et n'a pas
     * d'équivalent en JPQL. L'unité vient d'un paramètre lié, pas d'une
     * concaténation — et {@code HistoryGranularity} garantit en amont qu'elle
     * fait partie d'un jeu fermé.
     *
     * <p>Les huit mesures sont renvoyées d'un bloc plutôt que sélectionnées à la
     * demande : construire dynamiquement la liste des colonnes compliquerait la
     * requête pour économiser quelques octets sur des lignes déjà agrégées.
     *
     * <p>L'ordre des colonnes est repris tel quel par le service — le modifier
     * ici sans l'y reporter fausserait silencieusement toutes les statistiques.
     */
    @Query(value = """
           select date_trunc(:unit, r.recorded_at)                    as bucket,
                  count(*)                                            as sample_count,
                  count(*) filter (where r.anomaly_detected)          as anomaly_count,
                  min(r.temperature),  avg(r.temperature),  max(r.temperature),
                  min(r.humidite_sol), avg(r.humidite_sol), max(r.humidite_sol),
                  min(r.humidite_air), avg(r.humidite_air), max(r.humidite_air),
                  min(r.ph),           avg(r.ph),           max(r.ph),
                  min(r.azote),        avg(r.azote),        max(r.azote),
                  min(r.phosphore),    avg(r.phosphore),    max(r.phosphore),
                  min(r.potassium),    avg(r.potassium),    max(r.potassium),
                  min(r.luminosite),   avg(r.luminosite),   max(r.luminosite)
           from sensor_readings r
           where r.plot_id = :plotId
             and r.recorded_at >= :from
             and r.recorded_at <= :to
           group by bucket
           order by bucket
           """, nativeQuery = true)
    List<Object[]> aggregateHistory(@Param("plotId") Long plotId,
                                    @Param("unit") String unit,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);
}
