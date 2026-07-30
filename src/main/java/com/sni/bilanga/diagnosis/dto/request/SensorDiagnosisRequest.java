package com.sni.bilanga.diagnosis.dto.request;


import lombok.Data;

@Data
public class SensorDiagnosisRequest {
    private Long plotId;
    private String cropName;
    private String category;        // STRESS_HYDRIQUE, SOL_ACIDE, ...
    private Double confidence;
    private Long readingId;
}