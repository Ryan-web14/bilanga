package com.sni.bilanga.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DiseaseKnowledgeResponse {

    private Long id;
    private String cropName;

    /** Code normalisé, sans le préfixe du modèle de vision ({@code Tomato___}). */
    private String diseaseCode;

    private String displayName;
    private String symptoms;
    private String favorableConditions;
    private String treatment;
    private String prevention;

    private String priority;
    private String priorityLabel;

    /** Coût indicatif de l'action, par hectare. {@code null} = non renseigné, jamais « gratuit ». */
    private java.math.BigDecimal estimatedCost;
}
