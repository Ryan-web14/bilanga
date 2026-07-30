package com.sni.bilanga.knowledge.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RecommendationItem {

    private String content;
    private String type;       // BASE | CORRELATION
    private String priority;

    private String category;

    /** Identifiant de la règle déclenchée, lorsqu'une règle est en cause. */
    private Long sourceRuleId;

    /** Champ de mesure ayant fait basculer la décision. */
    private String measureField;

    private Double observedValue;

    private Double thresholdValue;

    /**
     * Coût indicatif de l'action, par hectare, hérité de la règle qui l'a produite
     * (V26, A11).
     *
     * <p>Reste {@code null} pour les conseils qu'aucune règle n'a produits —
     * synthèses d'arbitrage, projections de tendance, conseils météo : personne n'y
     * a chiffré quoi que ce soit, et fabriquer un montant serait présenter une
     * supposition comme un chiffrage.
     *
     * <p>{@code IrrigationAdapter} le préserve en reformulant, comme il préserve
     * les colonnes de traçabilité : le coût du paillage n'est pas celui de
     * l'irrigation, mais un coût faussé vaut mieux qu'un coût qui disparaît sans
     * qu'on sache pourquoi. À affiner le jour où les règles porteront des valeurs
     * sourcées.
     */
    private java.math.BigDecimal estimatedCost;
}