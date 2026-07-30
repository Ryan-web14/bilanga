package com.sni.bilanga.knowledge.dto.request;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DiseaseKnowledgeRequest {

    @NotBlank(message = "La culture est obligatoire")
    private String cropName;

    /** Code normalisé, tel que produit par le classifieur après retrait du préfixe. */
    @NotBlank(message = "Le code de la maladie est obligatoire")
    private String diseaseCode;

    private String displayName;
    private String symptoms;
    private String favorableConditions;

    @NotBlank(message = "Le traitement est obligatoire")
    private String treatment;

    private String prevention;
    private String priority;

    /**
     * Coût indicatif de l'action, PAR HECTARE (V26, A11).
     *
     * <p>Facultatif. Non renseigné, le conseil produit portera un coût nul —
     * ce qui est l'état de TOUTES les règles semées : aucun prix n'a été inventé,
     * un prix approximatif orientant une décision d'achat là où un seuil
     * approximatif n'orientait qu'une observation.
     *
     * <p>Zéro est licite et distinct de l'absence : certaines actions ne coûtent
     * que du temps.
     */
    @DecimalMin(value = "0.0", message = "Le coût estimé ne peut pas être négatif")
    private java.math.BigDecimal estimatedCost;
}
