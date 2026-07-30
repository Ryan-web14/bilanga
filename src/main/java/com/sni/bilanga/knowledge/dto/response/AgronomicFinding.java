package com.sni.bilanga.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class AgronomicFinding {

    // Aligné sur knowledge_rules.category : STRESS_HYDRIQUE, SOL_ACIDE...
    private String category;

    private String measureField;
    private Double observedValue;
    private Double thresholdValue;

    // Écart absolu au seuil franchi.
    private Double gap;

    // Écart rapporté au seuil, borné à 1. Sert à graduer la gravité. */
    private Double severityIndex;

    private String priority;

    // Constat chiffré, lisible tel quel
    private String statement;
}