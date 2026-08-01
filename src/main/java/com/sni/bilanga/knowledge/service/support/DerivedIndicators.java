package com.sni.bilanga.knowledge.service.support;

import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.model.CropRequirement;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calcule des indicateurs dérivés à partir de plusieurs mesures.
 *
 * Une mesure isolée est souvent ambiguë : 70 % d'humidité relative signifient
 * une chose à 18 °C et une autre à 32 °C. Les indicateurs ci-dessous lèvent
 * cette ambiguïté en combinant les grandeurs ou en les rapportant aux seuils
 * de la culture.
 *
 * Les plages d'interprétation retenues sont des références horticoles
 * courantes : elles doivent être validées auprès de sources agronomiques
 * avant toute exploitation en production.
 */
@Component
public class DerivedIndicators {

    // Bornes d'interprétation du déficit de pression de vapeur, en kPa
    public static final double VPD_CONFINED = 0.40;
    public static final double VPD_OPTIMAL_LOW = 0.80;
    public static final double VPD_OPTIMAL_HIGH = 1.20;
    public static final double VPD_EXCESSIVE = 1.60;  // au-delà : stress évaporatif

    public static final double NUTRIENT_IMBALANCE_LIMIT = 2.0;

    /**
     * Déficit de pression de vapeur (kPa), par l'équation de Tetens.
     *
     * Mesure la force avec laquelle l'air « tire » l'eau de la plante.
     * Un déficit faible signale un air saturé où la transpiration s'arrête et
     * où l'eau stagne sur le feuillage, conditions des maladies fongiques.
     * Un déficit élevé signale une demande évaporative que les racines ne
     * suivent pas, même en sol correctement pourvu.
     */
    public Double vaporPressureDeficit(Double temperature, Double relativeHumidity) {
        if (temperature == null || relativeHumidity == null) return null;

        double saturationPressure = 0.6108 * Math.exp((17.27 * temperature) / (temperature + 237.3));
        double vpd = saturationPressure * (1 - relativeHumidity / 100.0);
        return Math.max(0d, round(vpd, 3));
    }

    public String interpretVpd(Double vpd) {
        if (vpd == null) return null;
        if (vpd < VPD_CONFINED) return "Air confiné, transpiration entravée, conditions propices aux maladies fongiques";
        if (vpd < VPD_OPTIMAL_LOW) return "Demande évaporative faible";
        if (vpd <= VPD_OPTIMAL_HIGH) return "Demande évaporative optimale";
        if (vpd <= VPD_EXCESSIVE) return "Demande évaporative élevée";
        return "Stress évaporatif : la plante perd plus d'eau qu'elle n'en absorbe";
    }

    /**
     * Position d'une mesure dans la plage optimale, normalisée.
     * 0 correspond au seuil bas, 1 au seuil haut. En deçà de 0 il y a déficit,
     * au-delà de 1 il y a excès, et l'amplitude du dépassement est comparable
     * entre cultures aux exigences différentes.
     */
    public Double rangePosition(Double observed, Double min, Double max) {
        if (observed == null || min == null || max == null || max <= min) return null;
        return round((observed - min) / (max - min), 3);
    }

    public Map<String, Double> rangePositions(SensorReading r, CropRequirement req) {
        Map<String, Double> positions = new LinkedHashMap<>();
        putIfPresent(positions, "humidite_sol",
                rangePosition(r.getHumiditeSol(), req.getHumSolMin(), req.getHumSolMax()));
        putIfPresent(positions, "ph",
                rangePosition(r.getPh(), req.getPhMin(), req.getPhMax()));
        putIfPresent(positions, "temperature",
                rangePosition(r.getTemperature(), req.getTempMin(), req.getTempMax()));
        return positions;
    }

    /**
     * Rapport de chaque élément nutritif à son seuil minimal.
     * Une valeur de 1 signifie que le seuil est tout juste atteint.
     */
    public Map<String, Double> nutrientRatios(SensorReading r, CropRequirement req) {
        Map<String, Double> ratios = new LinkedHashMap<>();
        putIfPresent(ratios, "azote", ratio(r.getAzote(), req.getAzoteMin()));
        putIfPresent(ratios, "phosphore", ratio(r.getPhosphore(), req.getPhosphoreMin()));
        putIfPresent(ratios, "potassium", ratio(r.getPotassium(), req.getPotassiumMin()));
        return ratios;
    }

    /**
     * Écart entre l'élément le mieux pourvu et le moins bien pourvu.
     *
     * Les valeurs absolues ne suffisent pas : un excès d'azote entrave
     * l'assimilation du potassium, si bien qu'une fertilisation abondante mais
     * déséquilibrée peut produire une carence fonctionnelle sur un sol
     * pourtant riche.
     */
    public Double imbalance(Map<String, Double> ratios) {
        if (ratios.size() < 2) return null;

        double max = ratios.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double min = ratios.values().stream().mapToDouble(Double::doubleValue).min().orElse(0);
        if (min <= 0) return null;

        return round(max / min, 2);
    }

    public String dominantNutrient(Map<String, Double> ratios) {
        return ratios.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public String limitingNutrient(Map<String, Double> ratios) {
        return ratios.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Double ratio(Double observed, Double min) {
        if (observed == null || min == null || min == 0) return null;
        return round(observed / min, 3);
    }

    private void putIfPresent(Map<String, Double> map, String key, Double value) {
        if (value != null) map.put(key, value);
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}