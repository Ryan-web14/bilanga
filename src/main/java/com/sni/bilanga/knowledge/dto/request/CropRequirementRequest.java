package com.sni.bilanga.knowledge.dto.request;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CropRequirementRequest {

    @NotBlank(message = "Le nom de la culture est obligatoire")
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

    /** De 0 à 1. Atténue la gravité d'un déficit hydrique pour les cultures rustiques. */
    private Double toleranceSecheresse;
}
