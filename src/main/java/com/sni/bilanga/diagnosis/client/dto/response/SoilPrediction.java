package com.sni.bilanga.diagnosis.client.dto.response;


import lombok.Data;

@Data
public class SoilPrediction {
    private String category;       // ex. STRESS_HYDRIQUE
    private Double confidence;
}