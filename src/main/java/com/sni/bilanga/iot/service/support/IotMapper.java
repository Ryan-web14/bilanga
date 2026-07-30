package com.sni.bilanga.iot.service.support;


import com.sni.bilanga.enums.SensorHealth;
import com.sni.bilanga.iot.dto.response.IotDeviceResponse;
import com.sni.bilanga.iot.dto.response.ObservationResponse;
import com.sni.bilanga.iot.dto.response.SensorReadingResponse;
import com.sni.bilanga.iot.dto.response.SensorResponse;
import com.sni.bilanga.iot.model.IotDevice;
import com.sni.bilanga.iot.model.Observation;
import com.sni.bilanga.iot.model.Sensor;
import com.sni.bilanga.iot.model.SensorReading;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class IotMapper {

    public IotDeviceResponse toResponse(IotDevice d) {
        return IotDeviceResponse.builder()
                .id(d.getId())
                .plotId(d.getPlot().getId())
                .plotName(d.getPlot().getName())
                .technicalId(d.getTechnicalId())
                .deviceName(d.getDeviceName())
                .status(d.getStatus())
                .batteryLevel(d.getBatteryLevel())
                .batteryVoltage(d.getBatteryVoltage())
                .firmwareVersion(d.getFirmwareVersion())
                .lastSeenAt(d.getLastSeenAt())
                .minutesSinceLastSeen(minutesSince(d.getLastSeenAt()))
                .installedAt(d.getInstalledAt())
                .sensorHealth(d.getSensorHealth())
                .sensorHealthLabel(labelOfHealth(d.getSensorHealth()))
                .sensorHealthReason(d.getSensorHealthReason())
                .sensorHealthCheckedAt(d.getSensorHealthCheckedAt())
                .registeredAt(d.getRegisteredAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    /**
     * Âge du dernier contact, calculé ici pour que chaque client n'ait pas à
     * refaire la soustraction — et à se tromper de fuseau en la faisant.
     */
    private Long minutesSince(Instant lastSeenAt) {
        return lastSeenAt == null
                ? null
                : Duration.between(lastSeenAt, Instant.now()).toMinutes();
    }

    private String labelOfHealth(String stored) {
        SensorHealth health = SensorHealth.from(stored);
        return health == null ? null : health.getLabel();
    }

    public SensorResponse toResponse(Sensor s) {
        return SensorResponse.builder()
                .id(s.getId())
                .deviceId(s.getDevice().getId())
                .deviceName(s.getDevice().getDeviceName())
                .sensorType(s.getSensorType())
                .status(s.getStatus())
                .defaultValue(s.getDefaultValue())
                .addedAt(s.getAddedAt())
                .build();
    }

    public SensorReadingResponse toResponse(SensorReading r) {
        return SensorReadingResponse.builder()
                .id(r.getId())
                .plotId(r.getPlot().getId())
                .plotName(r.getPlot().getName())
                .deviceId(r.getDevice() == null ? null : r.getDevice().getId())
                .temperature(r.getTemperature())
                .temperatureSol(r.getTemperatureSol())
                .humiditeSol(r.getHumiditeSol())
                .humiditeAir(r.getHumiditeAir())
                .ph(r.getPh())
                .azote(r.getAzote())
                .phosphore(r.getPhosphore())
                .potassium(r.getPotassium())
                .luminosite(r.getLuminosite())
                .pluviometrie(r.getPluviometrie())
                .conductiviteElectrique(r.getConductiviteElectrique())
                .signalStrength(r.getSignalStrength())
                .quality(r.getQuality())
                .anomalyDetected(r.getAnomalyDetected())
                .recordedAt(r.getRecordedAt())
                .build();
    }

    public ObservationResponse toResponse(Observation o) {
        return ObservationResponse.builder()
                .id(o.getId())
                .plotId(o.getPlot().getId())
                .plotName(o.getPlot().getName())
                .userId(o.getUser() == null ? null : o.getUser().getId())
                .note(o.getNote())
                .photoUrl(o.getPhotoUrl())
                .observedAt(o.getObservedAt())
                .build();
    }
}