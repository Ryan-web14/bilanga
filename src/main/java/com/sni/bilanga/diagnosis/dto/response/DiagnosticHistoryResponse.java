package com.sni.bilanga.diagnosis.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DiagnosticHistoryResponse {

    private Long id;
    private Long plotId;
    private String plotName;
    private String source;
    private String result;

    /** Nom français du résultat — voir {@code DiseaseLabeller}. */
    private String resultLabel;
    private Double confidenceScore;
    private String confidenceLevel;
    private String cropName;
    private String imageUrl;
    private Long readingId;
    private String modelName;
    private Instant diagnosedAt;
    private List<RecommendationResponse> recommendations;
}