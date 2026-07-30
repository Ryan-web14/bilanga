package com.sni.bilanga.intervention.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Ce qu'une intervention a changé, mesuré.
 *
 * <p><strong>La contribution défendable de ce module.</strong> Le système ne se
 * contente plus de conseiller : il évalue ses propres conseils. C'est la seule
 * façon de démontrer qu'il sert à quelque chose, plutôt que de le postuler.
 *
 * <p><strong>Ce que ce verdict n'est pas.</strong> Une preuve de causalité. Une
 * humidité qui remonte après une irrigation a pu remonter grâce à elle, ou grâce
 * à une pluie survenue le même jour. {@link #limitation} le dit explicitement,
 * parce qu'un chiffre présenté sans réserve est lu comme une démonstration —
 * et que l'échantillon d'une exploitation n'autorise pas cette lecture.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class InterventionEffect {

    private Long interventionId;
    private String type;
    private String typeLabel;
    private Instant performedAt;

    /** Mesure censée bouger, ou {@code null} si l'effet n'est pas mesurable ainsi. */
    private String targetMeasure;
    private String targetMeasureLabel;

    /** Durée des fenêtres comparées, de part et d'autre de l'intervention. */
    private Integer windowHours;

    private Instant beforeFrom;
    private Instant beforeTo;
    private Instant afterFrom;
    private Instant afterTo;

    private Integer beforeSampleCount;
    private Integer afterSampleCount;

    private Double beforeAverage;
    private Double afterAverage;

    /** Écart absolu, {@code après − avant}. */
    private Double change;

    /** Écart rapporté à la valeur d'avant, en pourcentage. */
    private Double changePercent;

    /**
     * {@code AMELIORATION}, {@code AUCUN_CHANGEMENT}, {@code DEGRADATION} ou
     * {@code INDETERMINE}.
     *
     * <p>Le sens de l'amélioration dépend du type : une irrigation doit faire
     * <em>monter</em> l'humidité. Un même écart positif est donc un succès dans
     * un cas et un échec dans l'autre.
     */
    private String verdict;
    private String verdictLabel;

    /** Formulation chiffrée, directement affichable. */
    private String statement;

    /** Diagnostics anormaux avant/après — le seul angle pour un traitement. */
    private Integer abnormalDiagnosesBefore;
    private Integer abnormalDiagnosesAfter;

    /**
     * Ce que cette analyse ne permet pas de conclure. Toujours renseigné :
     * une comparaison avant/après n'établit jamais une causalité.
     */
    private String limitation;
}
