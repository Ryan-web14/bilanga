package com.sni.bilanga.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Exigences agronomiques générales d'une culture.
 *
 * Vue de lecture : l'entité était renvoyée telle quelle, ce qui exposait
 * jusqu'à sa colonne de version et liait le contrat d'API au schéma.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CropRequirementResponse {

    private Long id;
    private String cropName;

    private Double phMin;
    private Double phMax;
    private Double humSolMin;
    private Double humSolMax;
    private Double tempMin;
    private Double tempMax;
    private Double azoteMin;
    private Double phosphoreMin;
    private Double potassiumMin;
    private Double toleranceSecheresse;
}
