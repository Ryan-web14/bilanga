package com.sni.bilanga.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class IndicatorSet {

    /** Déficit de pression de vapeur, en kPa. Demande évaporative subie par la plante. */
    private Double vpd;

    /** Lecture agronomique du VPD. */
    private String vpdInterpretation;

    /**
     * Position de chaque mesure dans la plage optimale de la culture.
     * 0 = seuil bas, 1 = seuil haut, négatif = déficit, supérieur à 1 = excès.
     * Rend les mesures comparables d'une culture à l'autre.
     */
    private Map<String, Double> rangePosition;

    /** Rapport observé sur seuil, par élément nutritif. */
    private Map<String, Double> nutrientRatio;

    /** Rapport entre l'élément le mieux pourvu et le moins bien pourvu. */
    private Double nutrientImbalance;
}