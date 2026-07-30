package com.sni.bilanga.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Infléchissement des exigences pour un stade donné.
 *
 * Les bornes nulles ne sont pas des oublis : elles signifient « pas d'écart au
 * seuil général de la culture » — c'est le principe de la table, qui ne porte
 * que les différences.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CropStageRequirementResponse {

    private Long id;
    private String cropName;
    private String growthStage;
    private String growthStageLabel;
    private String label;

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
