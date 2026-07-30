package com.sni.bilanga.farm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * L'histoire agronomique d'une parcelle : les campagnes qui s'y sont succédé.
 *
 * <p><strong>Ce que cela permet de voir.</strong> Une parcelle n'est pas une suite de
 * campagnes indépendantes : ce qui y a poussé l'an dernier conditionne ce qui y pousse
 * cette année. Le système stockait toutes les campagnes sans jamais les présenter comme
 * une <em>suite</em> — impossible donc de repérer une monoculture, un sol laissé nu six
 * mois, ou un rendement qui décroche d'une campagne à l'autre.
 *
 * <p>Aucune donnée nouvelle n'a été nécessaire : tout se dérive de l'ordre des dates de
 * plantation.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PlotSuccession {

    private Long plotId;
    private String plotName;
    private String plotCode;

    /** Nombre de campagnes retenues — les erreurs de saisie sont exclues. */
    private Integer campaignCount;

    /** De la plus récente à la plus ancienne. */
    private List<Campaign> campaigns;

    /**
     * Périodes où la parcelle n'a rien porté, entre deux campagnes.
     *
     * <p>Ce n'est pas un défaut de saisie : une jachère est une pratique. Mais un
     * intervalle de trois ans se lit différemment d'un intervalle de trois semaines, et
     * seul l'affichage côte à côte permet de faire la différence.
     */
    private List<FallowPeriod> fallowPeriods;

    /**
     * Cultures répétées deux campagnes de suite ou plus.
     *
     * <p>Signal agronomique réel : la monoculture épuise le sol et concentre les
     * ravageurs spécifiques d'une espèce. Le système dispose de l'information depuis
     * toujours — il ne la disait à personne.
     */
    private List<String> monocultureWarnings;

    /**
     * Réserve, toujours renseignée.
     *
     * <p>La succession décrit ce qui a été <em>saisi</em>, non ce qui a poussé : une
     * campagne non déclarée laisse un trou que rien ne distingue d'une vraie jachère.
     */
    private String limitation;

    /** Ce qui manque pour lire l'historique sans réserve, expliqué. */
    private List<String> missingData;

    private Instant generatedAt;

    /**
     * Une campagne dans la suite.
     *
     * @param daysSincePrevious jours écoulés depuis la fin de la campagne précédente ;
     *                          {@code null} sur la plus ancienne. Négatif si les deux se
     *                          chevauchent — cas possible sur des données héritées, et
     *                          signalé plutôt qu'écrasé
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class Campaign {

        private Long cropId;
        private String cropName;
        private String variety;

        private LocalDate plantingDate;

        /**
         * Fin retenue pour le calcul des intervalles.
         *
         * <p>{@code actualEndDate} si la campagne a été close proprement, sinon
         * {@code expectedHarvestDate} — et {@code endDateIsEstimated} le dit.
         */
        private LocalDate endDate;

        /**
         * Vrai si {@link #endDate} est une <strong>estimation</strong>, faute de clôture
         * riche. À afficher : un intervalle calculé sur une date prévue n'a pas la même
         * valeur qu'un intervalle calculé sur un constat.
         */
        private Boolean endDateIsEstimated;

        private Integer durationDays;
        private Integer daysSincePrevious;

        private String status;
        private String closureReason;
        private String closureReasonLabel;

        /** Vrai si la campagne a effectivement produit — voir {@code CropClosureReason}. */
        private Boolean harvested;

        private Double plantedArea;

        /**
         * Bilan <strong>arrêté à la clôture</strong>, s'il existe.
         *
         * <p>Réémis tel quel. {@code null} sur les campagnes closes avant que le système
         * ne fige un bilan : l'information n'a jamais été demandée, elle n'est pas perdue.
         */
        private java.util.Map<String, Object> frozenEconomics;
    }

    /**
     * Une période de sol nu entre deux campagnes.
     *
     * @param days durée en jours ; un nombre, non une chaîne
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class FallowPeriod {
        private LocalDate from;
        private LocalDate to;
        private Integer days;

        /** Culture qui précédait — le précédent cultural, information agronomique. */
        private String previousCrop;
        private String nextCrop;
    }
}
