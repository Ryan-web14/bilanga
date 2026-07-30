package com.sni.bilanga.harvest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Bilan économique d'une parcelle sur une période.
 *
 * <p><strong>Entièrement calculé, rien de saisi.</strong> Le produit brut vient
 * des récoltes, les charges des interventions, la surface de la culture. Aucun
 * de ces chiffres n'est demandé à l'exploitant une seconde fois : c'est
 * précisément ce qui fait la valeur de l'agrégat, puisqu'un total ressaisi à la
 * main diverge dès la deuxième campagne.
 *
 * <p><strong>Ce que ce document ne démontre pas.</strong> Le taux de suivi des
 * conseils est mis en regard du rendement, et cette mise en regard est
 * <em>descriptive</em>. Une parcelle où les conseils ont été suivis et qui a
 * mieux produit ne prouve pas que les conseils en sont la cause : le sol, la
 * variété, la météo et l'attention générale portée à la parcelle varient
 * ensemble. {@link #limitation} le dit, parce qu'un chiffre livré sans réserve
 * est lu comme une démonstration.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PlotEconomics {

    private Long plotId;
    private String plotName;
    private String plotCode;

    private Long cropId;
    private String cropName;

    private LocalDate from;
    private LocalDate to;

    private String currency;

    // ============================================================
    // Produit
    // ============================================================

    private Integer harvestCount;

    /** Quantité totale récoltée, toutes récoltes de la période confondues. */
    private Double totalQuantity;
    private String quantityUnit;

    /** {@code Σ quantité × prix unitaire}. */
    private BigDecimal grossRevenue;

    // ============================================================
    // Charges
    // ============================================================

    private Integer interventionCount;

    private BigDecimal totalCost;

    /** Ventilation des charges par type d'intervention : où part l'argent. */
    private Map<String, BigDecimal> costByInterventionType;

    // ============================================================
    // Résultat
    // ============================================================

    /** {@code produit brut − charges}. Négative si la campagne a coûté plus qu'elle n'a rapporté. */
    private BigDecimal margin;

    /** Surface plantée, en hectares, telle que déclarée sur la culture. */
    private Double plantedArea;

    /** Marge rapportée à l'hectare : le seul chiffre comparable entre parcelles. */
    private BigDecimal marginPerHectare;

    /** Quantité récoltée par hectare. */
    private Double yieldPerHectare;

    /**
     * Part du produit brut absorbée par les charges, en pourcentage.
     * Au-delà de 100, la campagne est déficitaire.
     */
    private Double costRatio;

    // ============================================================
    // Mise en regard des conseils
    // ============================================================

    private Integer recommendationCount;
    private Integer appliedRecommendationCount;

    /** Part des conseils appliqués, en pourcentage. */
    private Double uptakeRate;

    /** Formulation en français du bilan, directement affichable. */
    private String summary;

    /**
     * Ce que ce bilan ne permet pas de conclure. Toujours renseigné.
     */
    private String limitation;

    /** Chiffres absents et raison de leur absence, pour que les vides s'expliquent. */
    private List<String> missingData;

    private Instant generatedAt;
}
