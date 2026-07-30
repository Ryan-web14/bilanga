package com.sni.bilanga.farm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * L'état de clôture d'une campagne : le bilan arrêté, le bilan vivant, et l'écart.
 *
 * <h2>Pourquoi les deux bilans, et non le seul figé</h2>
 *
 * <p>C'est la pièce qui réconcilie le bilan figé avec le contrat « rien n'est stocké »
 * de {@code MarginCalculator}. Ce contrat repose sur un argument juste : « un total mis
 * en cache diverge dès la première correction de saisie, et personne ne sait plus lequel
 * des deux chiffres croire ».
 *
 * <p>La réponse n'est pas de renoncer au figé — un bilan de campagne qui bouge n'est pas
 * un bilan de campagne. Elle est de <strong>rendre les deux, datés, avec leur
 * écart expliqué</strong>. Personne ne se demande alors lequel croire : le chiffre
 * arrêté est la référence de la campagne, et l'écart <em>devient</em> le signal
 * d'audit — il dit que quelque chose a été saisi, corrigé ou supprimé depuis.
 *
 * <p>Le cas concret : {@code harvestRepository.delete()} est une suppression
 * <strong>réelle</strong>, assumée. Une récolte supprimée après clôture rend le bilan
 * figé faux, et {@link #divergenceStatement} est exactement ce qui le rend visible.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CropClosureResponse {

    private Long cropId;
    private Long plotId;
    private String plotName;
    private String cropName;
    private String variety;

    private LocalDate plantingDate;

    /**
     * Date de fin <strong>réelle</strong>.
     *
     * <p>{@code null} sur une campagne close avant que le système ne demande un motif :
     * l'information n'a jamais été saisie, elle n'est pas perdue. À afficher comme telle
     * plutôt que comme une donnée manquante.
     */
    private LocalDate actualEndDate;

    /** Objectif calculé, pour mesurer si la campagne a tenu son calendrier. */
    private LocalDate expectedHarvestDate;

    /**
     * Écart, en jours, entre la fin réelle et la fin prévue.
     *
     * <p>Négatif si la campagne s'est achevée en avance. Un nombre, non une chaîne :
     * c'est un compteur.
     */
    private Integer daysVersusExpected;

    private String closureReason;
    private String closureReasonLabel;

    /** Vrai si la campagne a effectivement produit — voir {@code CropClosureReason}. */
    private Boolean harvested;

    private String closureNote;
    private Instant closedAt;
    private String closedByEmail;

    /**
     * Bilan <strong>arrêté à la clôture</strong>, tel qu'il a été figé.
     *
     * <p>Réémis tel quel, sans repasser par {@code PlotEconomics} : la conversion vers
     * {@code jsonb} est à sens unique, les identifiants y sont devenus des chaînes, et un
     * aller-retour les rendrait incohérents.
     *
     * <p>Porte {@code scope} et {@code zoneId} — en prévision du zonage de parcelle, la
     * seule information qu'aucune migration future ne pourrait reconstituer.
     */
    private Map<String, Object> frozenEconomics;

    /**
     * Bilan recalculé <strong>maintenant</strong>, sur les mêmes bornes.
     *
     * <p>Le contrat « tout se recalcule » vit ici : cette moitié n'est jamais stockée.
     */
    private Object currentEconomics;

    /** Vrai si au moins un montant ou volume a changé depuis la clôture. */
    private Boolean diverged;

    /** Les écarts, un par ligne, rédigés en français. */
    private List<String> divergenceChanges;

    /**
     * Formulation complète de l'écart, <strong>jamais nulle</strong>.
     *
     * <p>Y compris quand rien n'a bougé : « identique à celui arrêté à la clôture » est
     * une information rassurante, et un blanc obligerait le client à l'interpréter.
     */
    private String divergenceStatement;

    /**
     * Réserve, toujours renseignée.
     *
     * <p>Le rapprochement entre conseils suivis et rendement reste descriptif, et le
     * bilan figé est un arrêté de compte à une date — non une vérité définitive sur ce
     * que la campagne a valu.
     */
    private String limitation;

    private Instant generatedAt;
}
