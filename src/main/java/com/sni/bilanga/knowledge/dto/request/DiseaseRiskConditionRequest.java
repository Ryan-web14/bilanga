package com.sni.bilanga.knowledge.dto.request;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DiseaseRiskConditionRequest {

    @NotBlank(message = "La culture est obligatoire")
    private String cropName;

    @NotBlank(message = "Le code de la maladie est obligatoire")
    private String diseaseCode;

    @NotBlank(message = "Le champ de mesure est obligatoire")
    private String measureField;

    @NotBlank(message = "L'opérateur est obligatoire")
    private String operator;

    private Double threshold;
    private Double thresholdMax;

    /** Poids de la condition dans le calcul du risque. Vaut 1 par défaut. */
    private Double weight;

    @NotBlank(message = "Le libellé est obligatoire")
    private String label;

    private Boolean active;
}
