package com.sni.bilanga.farm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Cette campagne comparée à la précédente de la <strong>même culture</strong>.
 *
 * <p><strong>Pourquoi la même culture.</strong> Opposer une tomate à un manioc ne dit
 * rien : ni les rendements, ni les cycles, ni les charges ne sont commensurables. La
 * comparaison n'a de sens qu'à espèce constante — c'est ce qui permet de lire « 2 300
 * kg/ha contre 1 900 l'an dernier » comme une information et non comme un artefact.
 *
 * <p><strong>Ce que la comparaison n'établit pas.</strong> Un écart de rendement entre
 * deux campagnes ne dit pas ce qui l'a causé. La météo, la variété, la qualité du lot de
 * semence et l'attention portée à la parcelle varient toutes ensemble, et une
 * exploitation ne fournit pas l'échantillon qui permettrait de les démêler. C'est
 * pourquoi {@link #limitation} est toujours renseignée.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CropComparison {

    private Long plotId;
    private String plotName;
    private String cropName;

    /** La campagne examinée. */
    private PlotSuccession.Campaign current;

    /**
     * La campagne précédente de la même culture.
     *
     * <p>{@code null} s'il n'y en a pas — premier passage de cette culture sur cette
     * parcelle. {@link #summary} l'énonce alors explicitement plutôt que de rendre une
     * comparaison vide.
     */
    private PlotSuccession.Campaign previous;

    /** Vrai si une campagne antérieure a été trouvée. */
    private Boolean comparable;

    /**
     * Les écarts chiffrés, un par indicateur.
     *
     * <p>Vide quand aucune campagne antérieure n'existe, ou quand aucun des deux côtés
     * ne porte de bilan.
     */
    private List<Metric> metrics;

    /** Résumé rédigé, prêt à afficher. */
    private String summary;

    /**
     * Réserve, <strong>toujours renseignée</strong>.
     *
     * <p>Un écart constate, il n'explique pas. L'afficher sans réserve ferait lire une
     * corrélation comme une cause — et c'est exactement l'erreur qu'un exploitant
     * commettra s'il a suivi les conseils cette année et pas l'an dernier.
     */
    private String limitation;

    /** Ce qui empêche une comparaison complète, expliqué ligne par ligne. */
    private List<String> missingData;

    private Instant generatedAt;

    /**
     * L'écart sur un indicateur.
     *
     * @param changePercent variation relative ; {@code null} si la valeur antérieure est
     *                      nulle — diviser par zéro afficherait « +∞ », ce qui ne se lit
     *                      pas
     * @param better        {@code true} si l'évolution va dans le sens souhaitable,
     *                      {@code false} sinon, {@code null} quand la direction n'a pas
     *                      de sens (les charges peuvent monter pour de bonnes raisons)
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class Metric {

        /** Clé stable : {@code yieldPerHectare}, {@code marginPerHectare}, … */
        private String key;

        /** Libellé français, prêt à afficher. */
        private String label;

        private String unit;

        private Double previousValue;
        private Double currentValue;
        private Double change;
        private Double changePercent;

        private Boolean better;

        /** Formulation complète de l'écart, en français. */
        private String statement;
    }
}
