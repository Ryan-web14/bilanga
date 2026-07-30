package com.sni.bilanga.weather.model;

import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.farm.model.Plot;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Prévision météo pour une parcelle, à une échéance donnée.
 *
 * <p><strong>Pourquoi une table et non un cache en mémoire.</strong> Le
 * fournisseur borne le nombre d'appels, et une prévision obtenue il y a vingt
 * minutes reste juste. Une table survit en outre au redémarrage — sans quoi
 * chaque relance provoquerait une rafale d'appels pour reconstituer ce qu'on
 * venait de jeter.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "weather_forecast")
public class WeatherForecast {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_forecast_plot"))
    private Plot plot;

    /** Échéance visée. */
    @Column(name = "forecast_at", nullable = false)
    private Instant forecastAt;

    /**
     * Instant d'obtention — l'âge du cache, à ne pas confondre avec l'échéance.
     * Une prévision de demain obtenue il y a trois jours ne vaut pas la même
     * obtenue il y a une heure.
     */
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "humidite")
    private Double humidite;

    /** Cumul attendu sur l'heure, en mm. */
    @Column(name = "precipitation_mm")
    private Double precipitationMm;

    @Column(name = "wind_speed")
    private Double windSpeed;

    @Column(name = "cloud_cover")
    private Double cloudCover;

    @Column(name = "provider", length = 30)
    private String provider;

    @PrePersist
    void onCreate() {
        if (fetchedAt == null) fetchedAt = Instant.now();
    }
}
