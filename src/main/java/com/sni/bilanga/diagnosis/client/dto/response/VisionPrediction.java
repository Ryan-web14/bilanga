package com.sni.bilanga.diagnosis.client.dto.response;


import lombok.Data;

import java.util.Map;

@Data
public class VisionPrediction {

    private String crop;
    private String diseaseClass;
    private Double confidence;

    /** Distribution complète, exploitée quand la confiance est faible. */
    private Map<String, Double> allProbabilities;
}