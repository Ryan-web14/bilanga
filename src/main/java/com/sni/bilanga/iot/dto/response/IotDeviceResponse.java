package com.sni.bilanga.iot.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class IotDeviceResponse {

    private Long id;
    private Long plotId;
    private String plotName;
    private String technicalId;
    private String deviceName;
    private String status;
    private Integer batteryLevel;
    private Double batteryVoltage;
    private String firmwareVersion;

    /**
     * Dernier contact du boîtier, quelle qu'en soit la nature.
     *
     * Distingue « boîtier muet » de « parcelle sans relevé » : la vue d'ensemble
     * déduisait jusqu'ici le premier de la seconde.
     */
    private Instant lastSeenAt;

    /** Minutes écoulées depuis le dernier contact ; nul si le boîtier n'a jamais parlé. */
    private Long minutesSinceLastSeen;

    private Instant installedAt;

    /**
     * Fiabilité des sondes : {@code SAINE}, {@code SUSPECTE}, {@code DEFAILLANTE}.
     *
     * Distinct de {@link #status}, qui dit si le boîtier est en service. Un
     * boîtier peut être parfaitement ACTIVE et remonter des mesures fausses.
     */
    private String sensorHealth;
    private String sensorHealthLabel;

    /** Motif du verdict : c'est lui qui dit quelle sonde changer. */
    private String sensorHealthReason;

    private Instant sensorHealthCheckedAt;

    private Instant registeredAt;
    private Instant updatedAt;
}