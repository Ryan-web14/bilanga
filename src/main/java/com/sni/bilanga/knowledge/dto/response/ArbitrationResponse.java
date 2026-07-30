package com.sni.bilanga.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Synthèse à produire lorsque deux catégories de conseils coexistent et se
 * contredisent — « baisser l'humidité » et « irriguer », par exemple.
 *
 * L'arbitrage <strong>ajoute</strong> la synthèse sans retirer les conseils
 * qu'elle concilie : la traçabilité du raisonnement est conservée.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ArbitrationResponse {

    private Long id;
    private String cropName;

    private String categoryA;
    private String categoryB;

    private String synthesis;

    private String priority;
    private String priorityLabel;

    private Boolean active;
}
