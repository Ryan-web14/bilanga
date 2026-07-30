package com.sni.bilanga.knowledge.dto.request;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArbitrationRequest {

    /** Culture ciblée, ou vide pour toutes. */
    private String cropName;

    @NotBlank(message = "La première catégorie est obligatoire")
    private String categoryA;

    @NotBlank(message = "La seconde catégorie est obligatoire")
    private String categoryB;

    @NotBlank(message = "La synthèse est obligatoire")
    private String synthesis;

    private String priority;
    private Boolean active;
}
