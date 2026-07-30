package com.sni.bilanga.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Une des conditions d'apparition d'une maladie, pondérée.
 *
 * Le score de risque est la fraction de poids satisfait sur le poids total :
 * {@code weight} n'est donc pas décoratif, il détermine le poids relatif de
 * cette condition dans la conclusion.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DiseaseRiskConditionResponse {

    private Long id;
    private String cropName;
    private String diseaseCode;

    private String measureField;
    private String measureLabel;

    private String operator;
    private Double threshold;

    /** Borne haute, utilisée uniquement par l'opérateur {@code BETWEEN}. */
    private Double thresholdMax;

    private Double weight;
    private String label;

    /** Une condition désactivée est ignorée du calcul sans être supprimée. */
    private Boolean active;

    /** Formulation lisible de la condition, ex. « humidité de l'air > 85 ». */
    private String expression;
}
