package com.sni.bilanga.iot.model;


import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.farm.model.Plot;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Relevé complet de capteurs pour une parcelle à un instant t.
 * Entrée du modèle tabulaire et source des règles de corrélation.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "sensor_readings")
public class SensorReading {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reading_plot"))
    private Plot plot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", foreignKey = @ForeignKey(name = "fk_reading_device"))
    private IotDevice device;

    /**
     * Température de l'<strong>air</strong>, en °C.
     *
     * Le nom ne le dit pas, l'usage si : c'est cette mesure que les moteurs
     * confrontent aux seuils {@code tempMin/tempMax} de la culture, et c'est
     * elle que le microservice d'inférence reçoit sous la clé
     * {@code temperature}. Renommer la colonne casserait à la fois le contrat
     * d'API et la feature map ; on documente donc, et {@link #temperatureSol}
     * porte la mesure du sol.
     */
    @Column(name = "temperature")
    private Double temperature;

    /**
     * Température du <strong>sol</strong>, en °C.
     *
     * Elle commande la germination et la tubérisation du manioc, que la
     * température de l'air ne renseigne qu'indirectement.
     */
    @Column(name = "temperature_sol")
    private Double temperatureSol;

    @Column(name = "humidite_sol")
    private Double humiditeSol;

    @Column(name = "humidite_air")
    private Double humiditeAir;

    @Column(name = "ph")
    private Double ph;

    @Column(name = "azote")
    private Double azote;

    @Column(name = "phosphore")
    private Double phosphore;

    @Column(name = "potassium")
    private Double potassium;

    @Column(name = "luminosite")
    private Double luminosite;

    /**
     * Pluie relevée depuis la mesure précédente, en mm.
     *
     * Au Congo la saison des pluies structure tout : sans cette mesure, le
     * moteur conseille d'irriguer un sol que la pluie va saturer dans l'heure.
     */
    @Column(name = "pluviometrie")
    private Double pluviometrie;

    /** Conductivité électrique du sol : salinité et charge en engrais. Complète N/P/K. */
    @Column(name = "conductivite_electrique")
    private Double conductiviteElectrique;

    /**
     * Puissance du signal reçu par le boîtier, en dBm (donc négative).
     * Distingue « sonde en panne » de « couverture réseau faible ».
     */
    @Column(name = "signal_strength")
    private Integer signalStrength;

    @Column(name = "quality")
    private String quality;

    @Column(name = "anomaly_detected")
    private Boolean anomalyDetected;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @PrePersist
    void onCreate() {
        if (recordedAt == null) recordedAt = Instant.now();
    }
}