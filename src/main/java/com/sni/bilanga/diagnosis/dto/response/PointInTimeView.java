package com.sni.bilanga.diagnosis.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ce que le système voyait, ce qu'il avait conclu, et ce qu'il conclurait
 * aujourd'hui — à un instant choisi.
 *
 * <h2>Le manque comblé</h2>
 *
 * <p>{@code GET /plots/{id}/history} rend des intervalles agrégés : un point de
 * courbe porte un {@code bucket}, un décompte et des min/moy/max, mais <strong>ni
 * identifiant de relevé, ni identifiant de diagnostic</strong>. Cliquer sur un creux
 * d'humidité du 12 mars ne menait donc nulle part — alors que c'est exactement le
 * geste qu'on fait pour comprendre un incident.
 *
 * <p>Cette vue est le chaînon manquant : elle prend un instant brut (le
 * {@code bucket} du point cliqué convient très bien) et résout elle-même le relevé,
 * la conclusion d'époque, et la conclusion actuelle.
 *
 * <h2>Deux notions de « le diagnostic d'alors », et pourquoi les deux</h2>
 *
 * <p>C'est la décision de conception qui commande tout le reste.
 *
 * <p>Le diagnostic <strong>issu de ce relevé</strong> est rare. {@code DiagnosisThrottle}
 * n'autorise une conclusion qu'au-delà d'un intervalle minimal et à condition qu'une
 * mesure ait bougé ; s'y ajoutent {@code SONDE_DEFAILLANTE}, {@code CONTEXTE_ABSENT}
 * et {@code ML_INDISPONIBLE}, qui abandonnent le diagnostic <em>en conservant le
 * relevé</em>. Sur un boîtier qui émet toutes les trente secondes, la grande majorité
 * des relevés n'ont aucune conclusion attachée. <strong>C'est le cas nominal, pas
 * l'exception.</strong>
 *
 * <p>Le diagnostic <strong>en vigueur</strong> — le dernier antérieur — existe
 * presque toujours, et c'est celui que l'exploitant avait effectivement sous les
 * yeux.
 *
 * <p>Une vue qui n'aurait cherché que le premier aurait rendu « aucun diagnostic »
 * dans le cas ordinaire et serait passée pour cassée. Une vue qui n'aurait cherché
 * que le second aurait attribué à une mesure une conclusion qu'elle n'a pas produite.
 * D'où {@link #alignment}, qui dit laquelle a répondu — sans quoi l'utilisateur ne
 * peut pas savoir ce qu'il regarde.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PointInTimeView {

    private Long plotId;
    private String plotName;
    private String cropName;

    /** Instant demandé, tel quel — pour que le client puisse recaler son curseur. */
    private Instant requestedAt;

    /** Relevé retenu, ou {@code null} si la parcelle n'en a aucun d'exploitable. */
    private ReadingRef reading;

    /**
     * Comment le relevé a été trouvé : {@code EXACT}, {@code AVANT}, {@code APRES}
     * ou {@code AUCUN}.
     *
     * <p>À afficher. « Mesures du 12 mars à 8 h 05 » et « mesures les plus proches,
     * relevées 40 minutes plus tôt » n'appellent pas la même confiance, et masquer
     * l'écart ferait passer une approximation pour une lecture directe.
     */
    private String readingSelection;

    /** Conclusion d'époque, ou {@code null} — voir {@link #alignment}. */
    private DiagnosticRef diagnosedThen;

    /**
     * Provenance de la conclusion d'époque.
     *
     * <ul>
     *   <li>{@code SUR_CE_RELEVE} — le diagnostic est issu de ce relevé précis. Le
     *       cas le plus fort, et le plus rare.</li>
     *   <li>{@code EN_VIGUEUR} — le diagnostic est le dernier antérieur : il ne vient
     *       pas de ce relevé, mais c'est bien lui qui s'affichait alors. Le cas
     *       ordinaire ; {@link #diagnosticAgeMinutes} en donne l'ancienneté.</li>
     *   <li>{@code AUCUN} — aucune conclusion n'existait, et ce n'est pas une
     *       anomalie.</li>
     * </ul>
     */
    private String alignment;

    /**
     * Âge de la conclusion au moment demandé, en minutes.
     *
     * <p>Un nombre, non une chaîne : c'est un compteur, pas un identifiant. Vaut
     * {@code 0} sur {@code SUR_CE_RELEVE}, {@code null} sur {@code AUCUN}.
     */
    private Integer diagnosticAgeMinutes;

    /**
     * Ce que la base de connaissance <strong>actuelle</strong> conclurait sur ces
     * mesures.
     *
     * <p>Même structure que le côté gauche, pour que les deux s'affichent côte à côte
     * sans transformation. {@code null} si aucun relevé n'a pu être retenu.
     */
    private DiagnosisReplay.Snapshot nowWouldConclude;

    /**
     * Écarts entre la conclusion d'époque et la conclusion actuelle.
     *
     * <p>Vide lorsque rien n'a changé — l'information la plus fréquente et la plus
     * rassurante. Vide également quand il n'y avait aucune conclusion : il n'y a alors
     * rien à comparer, et {@link #limitation} le dit.
     */
    private List<DiagnosisReplay.Difference> differences;

    /** Vrai si {@link #differences} est vide, pour éviter un test côté client. */
    private Boolean identical;

    /** Résumé rédigé, prêt à afficher. */
    private String summary;

    /**
     * Réserves, <strong>toujours renseignées</strong>.
     *
     * <p>Cette vue superpose trois choses d'inégale solidité : des mesures
     * enregistrées (exactes), une conclusion d'époque (peut-être seulement
     * contemporaine), et un recalcul partiel (les moteurs déterministes seuls). Livrée
     * sans réserve, elle se lirait comme une reconstitution fidèle.
     */
    private String limitation;

    private Instant generatedAt;

    /**
     * Le relevé retenu et son écart à l'instant demandé.
     *
     * @param offsetMinutes écart <strong>signé</strong> : négatif si le relevé
     *                      précède l'instant demandé, positif s'il le suit. Le signe
     *                      importe — « 40 minutes plus tôt » et « 40 minutes plus
     *                      tard » ne se valent pas quand on cherche la cause d'un
     *                      événement.
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class ReadingRef {

        private Long id;
        private Instant recordedAt;
        private Integer offsetMinutes;

        /** Mesures non nulles du relevé, clés du vocabulaire des moteurs. */
        private Map<String, Double> measures;

        /** Vrai si au moins une mesure était hors des valeurs physiquement possibles. */
        private Boolean anomalyDetected;
    }

    /** La conclusion d'époque, réduite à ce qui s'affiche. */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class DiagnosticRef {

        private Long id;
        private String source;
        private String result;
        private Double confidenceScore;
        private String confidenceLevel;
        private Boolean reliable;
        private Instant diagnosedAt;

        /** Identifiant du relevé qui l'a produit — permet de vérifier l'alignement. */
        private Long readingId;

        private Integer recommendationCount;
        private List<DiagnosisReplay.Snapshot.Line> recommendations;
    }
}
