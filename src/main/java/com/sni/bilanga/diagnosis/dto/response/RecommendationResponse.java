package com.sni.bilanga.diagnosis.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RecommendationResponse {

    private Long id;
    private Long diagnosticId;
    private Long plotId;

    private String content;

    private String recommendationType;
    private String recommendationTypeLabel;

    private String priority;
    private String priorityLabel;

    private String status;
    private String statusLabel;

    private Long sourceRuleId;
    private String measureField;
    private Double observedValue;
    private Double thresholdValue;

    /** Coût estimé de la mise en œuvre ; nul quand la règle n'en porte pas. */
    private java.math.BigDecimal estimatedCost;

    private Instant createdAt;

    /** Date de la réponse de l'exploitant ; nulle tant que le conseil est à traiter. */
    private Instant feedbackAt;

    private String feedbackNote;
}