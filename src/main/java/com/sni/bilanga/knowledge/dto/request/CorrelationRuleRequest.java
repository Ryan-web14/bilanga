package com.sni.bilanga.knowledge.dto.request;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CorrelationRuleRequest {

    /** Culture ciblée, ou vide pour toutes. */
    private String cropName;

    /** Maladie ciblée, ou vide pour toutes. */
    private String diseaseCode;

    @NotBlank(message = "Le champ de mesure est obligatoire")
    private String measureField;

    @NotBlank(message = "L'opérateur est obligatoire")
    private String operator;

    private Double threshold;

    @NotBlank(message = "La recommandation complémentaire est obligatoire")
    private String extraRecommendation;

    private String priority;
}
