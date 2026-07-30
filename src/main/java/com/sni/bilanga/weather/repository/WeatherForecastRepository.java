package com.sni.bilanga.weather.repository;

import com.sni.bilanga.weather.model.WeatherForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {

    /** Prévisions couvrant la fenêtre demandée, de la plus proche à la plus lointaine. */
    List<WeatherForecast> findByPlot_IdAndForecastAtBetweenOrderByForecastAtAsc(
            Long plotId, Instant from, Instant to);

    Optional<WeatherForecast> findByPlot_IdAndForecastAt(Long plotId, Instant forecastAt);

    /**
     * Fraîcheur du cache : la plus récente obtention pour cette parcelle.
     *
     * C'est elle qui décide s'il faut redemander, et non l'existence de lignes —
     * une table pleine de prévisions périmées passerait sinon pour un cache
     * valide.
     */
    @Query("select max(f.fetchedAt) from WeatherForecast f where f.plot.id = :plotId")
    Optional<Instant> findLastFetchAt(@Param("plotId") Long plotId);

    /**
     * Purge des échéances dépassées.
     *
     * Sans elle la table croît indéfiniment : une prévision d'hier n'a aucun
     * usage, et l'historique météo n'est pas ce que ce cache a vocation à porter.
     */
    @Modifying
    @Query("delete from WeatherForecast f where f.plot.id = :plotId and f.forecastAt < :before")
    int deleteExpired(@Param("plotId") Long plotId, @Param("before") Instant before);

    /**
     * Purge globale des échéances dépassées, toutes parcelles confondues (A14).
     *
     * <p><strong>Le défaut corrigé.</strong> {@link #deleteExpired} ne s'exécute
     * qu'au rafraîchissement d'<em>une</em> parcelle. Une parcelle qui cesse d'être
     * interrogée — culture terminée, boîtier retiré, exploitation archivée —
     * conservait donc indéfiniment ses prévisions périmées : plus personne ne
     * déclenchait le nettoyage qui les concernait, précisément parce que plus
     * personne ne s'y intéressait.
     *
     * <p>C'est le profil de fuite le plus discret : la table grossit d'autant plus
     * que les parcelles sont abandonnées, et rien dans le fonctionnement normal ne
     * le signale.
     */
    @Modifying
    @Query("delete from WeatherForecast f where f.forecastAt < :before")
    int purgeExpired(@Param("before") Instant before);
}
