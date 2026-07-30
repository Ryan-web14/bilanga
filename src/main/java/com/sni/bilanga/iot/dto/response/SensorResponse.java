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
public class SensorResponse {

    private Long id;
    private Long deviceId;
    private String deviceName;
    private String sensorType;
    private String status;
    private Double defaultValue;
    private Instant addedAt;
}