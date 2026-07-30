package com.sni.bilanga.knowledge.dto.request;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Un champ laissé vide signifie que le stade n'infléchit pas ce seuil :
 * la valeur générale de la culture continue de s'appliquer.
 */
@Data
public class CropStageRequirementRequest {

    @NotBlank(message = "La culture est obligatoire")
    private String cropName;

    @NotBlank(message = "Le stade de croissance est obligatoire")
    private String growthStage;

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
