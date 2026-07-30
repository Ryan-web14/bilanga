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
public class SensorReadingResponse {

    private Long id;
    private Long plotId;
    private String plotName;
    private Long deviceId;

    /** Température de l'air, en °C : celle que les moteurs comparent aux seuils. */
    private Double temperature;

    /** Température du sol, en °C. */
    private Double temperatureSol;

    private Double humiditeSol;
    private Double humiditeAir;
    private Double ph;
    private Double azote;
    private Double phosphore;
    private Double potassium;
    private Double luminosite;
    private Double pluviometrie;
    private Double conductiviteElectrique;
    private Integer signalStrength;
    private String quality;
    private Boolean anomalyDetected;
    private Instant recordedAt;
}