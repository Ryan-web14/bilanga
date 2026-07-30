package com.sni.bilanga.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Conseil supplémentaire déclenché lorsqu'une maladie diagnostiquée par l'image
 * coïncide avec une mesure franchissant un seuil.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CorrelationRuleResponse {

    private Long id;
    private String cropName;
    private String diseaseCode;

    private String measureField;
    private String measureLabel;

    private String operator;
    private Double threshold;

    private String extraRecommendation;

    private String priority;
    private String priorityLabel;

    private String expression;
}
