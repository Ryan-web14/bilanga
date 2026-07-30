package com.sni.bilanga.diagnosis.dto.request;


import lombok.Data;

@Data
public class ImageDiagnosisRequest {
    private Long plotId;
    private String cropName;
    private String diseaseClass;    // classe brute du modèle, ex. Tomato___Late_blight
    private Double confidence;
    private Long readingId;         // optionnel : relevé capteur pour la corrélation
    private String imageUrl;
}