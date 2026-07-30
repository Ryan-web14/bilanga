package com.sni.bilanga.knowledge.dto.request;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeRuleRequest {

    @NotBlank(message = "La catégorie est obligatoire")
    private String category;

    /** Culture ciblée, ou vide pour toutes. */
    private String cropName;

    private String conditionText;

    @NotBlank(message = "L'action proposée est obligatoire")
    private String proposedAction;

    private String priority;
    private Boolean validated;

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
