package com.sni.bilanga.iot.model;


import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.farm.model.Plot;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "iot_devices")
public class IotDevice {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    /**
     * Verrou optimiste : une modification concurrente du même enregistrement
     * échoue au lieu d'écraser silencieusement la précédente (migration V12).
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_device_plot"))
    private Plot plot;

    /** Identifiant gravé sur le boîtier, utilisé par le terrain pour l'appairage. */
    @Column(name = "technical_id", nullable = false, unique = true)
    private String technicalId;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "status")
    private String status;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    /**
     * Tension brute de la batterie, en volts.
     *
     * Elle vieillit mieux que le pourcentage : la conversion tension → charge
     * dépend du modèle de batterie et se dégrade avec l'âge de la cellule.
     */
    @Column(name = "battery_voltage")
    private Double batteryVoltage;

    /**
     * Dernier contact du boîtier, quelle qu'en soit la nature — dépôt de relevé
     * ou simple appel de liveness.
     *
     * <p>La vue d'ensemble déduisait jusqu'ici le silence d'un boîtier de
     * l'absence de relevé, ce qui confond deux situations distinctes : un
     * boîtier muet, et une parcelle qui n'a simplement jamais été instrumentée.
     * Une colonne dédiée lève l'ambiguïté.
     */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /** Indispensable dès qu'il y a plus de trois boîtiers sur le terrain. */
    @Column(name = "firmware_version", length = 40)
    private String firmwareVersion;

    /** Pose sur le terrain — à distinguer de l'enregistrement en base. */
    @Column(name = "installed_at")
    private Instant installedAt;

    /**
     * Fiabilité des sondes, vocabulaire de {@code SensorHealth}.
     *
     * <p>Distinct de {@link #status}, qui dit si le boîtier est en service. Un
     * boîtier peut être parfaitement ACTIVE et remonter des mesures fausses —
     * c'est même le cas le plus dangereux, puisque rien ne le signale.
     */
    @Column(name = "sensor_health", length = 20)
    private String sensorHealth;

    /** Motif du verdict, en clair : c'est lui qui dit quelle sonde changer. */
    @Column(name = "sensor_health_reason", length = 300)
    private String sensorHealthReason;

    @Column(name = "sensor_health_checked_at")
    private Instant sensorHealthCheckedAt;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (registeredAt == null) registeredAt = Instant.now();
    }
}