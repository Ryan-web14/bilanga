package com.sni.bilanga.iot.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Sonde individuelle portée par un boîtier (humidité, pH, température...).
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "sensors")
public class Sensor {

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
    @JoinColumn(name = "device_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sensor_device"))
    private IotDevice device;

    @Column(name = "sensor_type", nullable = false)
    private String sensorType;

    @Column(name = "status")
    private String status;

    @Column(name = "default_value")
    private Double defaultValue;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @PrePersist
    void onCreate() {
        if (addedAt == null) addedAt = Instant.now();
    }
}