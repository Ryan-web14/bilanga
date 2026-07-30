package com.sni.bilanga.knowledge.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Évolution d'une mesure dans le temps, projetée jusqu'au franchissement
 * d'un seuil.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class TrendFinding {

    private String measureField;

    /** Variation par heure, positive à la hausse. */
    private Double slopePerHour;

    private Double currentValue;

    /** Seuil vers lequel la mesure se dirige. */
    private Double thresholdValue;

    /** Délai estimé avant franchissement, en heures. */
    private Double hoursToThreshold;

    /** Nombre de relevés ayant servi au calcul. */
    private Integer sampleSize;

    /**
     * Coefficient de détermination de la régression, entre 0 et 1.
     *
     * C'est la part de la variation expliquée par la droite. Sans lui, une série
     * de mesures erratiques produisait une pente arbitraire — et une projection
     * annoncée à l'exploitant avec le même aplomb qu'une tendance nette.
     */
    private Double rSquared;

    /** Lecture du {@code rSquared} : {@code ELEVEE}, {@code MOYENNE} ou {@code FAIBLE}. */
    private String fitQuality;

    private String priority;

    private String statement;
}
