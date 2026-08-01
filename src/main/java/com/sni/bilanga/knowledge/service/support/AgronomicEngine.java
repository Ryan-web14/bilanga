package com.sni.bilanga.knowledge.service.support;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.AgronomicFinding;
import com.sni.bilanga.knowledge.dto.response.IndicatorSet;
import com.sni.bilanga.knowledge.model.CropRequirement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Moteur de règles agronomiques.
 *
 * Confronte les mesures aux seuils de la culture et aux indicateurs dérivés,
 * puis produit des constats chiffrés. Voie de raisonnement déterministe,
 * complémentaire des modèles statistiques : elle reste valable quand le modèle
 * hésite, et elle justifie chacune de ses conclusions.
 */
@Component
@RequiredArgsConstructor
public class AgronomicEngine {

    private static final Locale FR = Locale.FRANCE;

    private final CropRequirementResolver requirementResolver;
    private final DerivedIndicators indicators;

    /** En deçà, l'écart au seuil est jugé négligeable et n'est pas signalé. */
    private final BilangaProperties.Agronomic agronomic;

    // ============================================================
    // Analyse
    // ============================================================
    public List<AgronomicFinding> analyze(String cropName, String growthStage, SensorReading reading) {
        List<AgronomicFinding> findings = new ArrayList<>();
        CropRequirement req = requirement(cropName, growthStage, reading);
        if (req == null) return findings;

        double tolerance = req.getToleranceSecheresse() == null ? 0d : req.getToleranceSecheresse();

        analyzeRawMeasures(findings, cropName, reading, req, tolerance);
        analyzeVpd(findings, reading);
        analyzeNutrientBalance(findings, reading, req);

        return findings;
    }

    public IndicatorSet indicators(String cropName, String growthStage, SensorReading reading) {
        CropRequirement req = requirement(cropName, growthStage, reading);
        if (req == null) return null;

        Double vpd = indicators.vaporPressureDeficit(reading.getTemperature(), reading.getHumiditeAir());
        Map<String, Double> ratios = indicators.nutrientRatios(reading, req);

        return IndicatorSet.builder()
                .vpd(vpd)
                .vpdInterpretation(indicators.interpretVpd(vpd))
                .rangePosition(indicators.rangePositions(reading, req))
                .nutrientRatio(ratios)
                .nutrientImbalance(indicators.imbalance(ratios))
                .build();
    }

    // ============================================================
    // Mesures brutes contre seuils de la culture
    // ============================================================
    private void analyzeRawMeasures(List<AgronomicFinding> out, String crop, SensorReading r,
                                    CropRequirement req, double tolerance) {

        // La tolérance à la sécheresse atténue la gravité du déficit hydrique,
        // sans déplacer le seuil optimal de la culture.
        below(out, "humidite_sol", "Humidité du sol", "%", r.getHumiditeSol(),
                req.getHumSolMin(), req.getHumSolMax(), "STRESS_HYDRIQUE", crop, tolerance);
        above(out, "humidite_sol", "Humidité du sol", "%", r.getHumiditeSol(),
                req.getHumSolMax(), req.getHumSolMin(), "EXCES_EAU", crop, 0d);

        below(out, "ph", "pH du sol", "", r.getPh(),
                req.getPhMin(), req.getPhMax(), "SOL_ACIDE", crop, 0d);
        above(out, "ph", "pH du sol", "", r.getPh(),
                req.getPhMax(), req.getPhMin(), "SOL_ALCALIN", crop, 0d);

        below(out, "temperature", "Température", " °C", r.getTemperature(),
                req.getTempMin(), req.getTempMax(), "STRESS_THERMIQUE", crop, 0d);
        above(out, "temperature", "Température", " °C", r.getTemperature(),
                req.getTempMax(), req.getTempMin(), "STRESS_THERMIQUE", crop, 0d);

        below(out, "azote", "Azote", "", r.getAzote(),
                req.getAzoteMin(), null, "CARENCES_NUTRITIVES", crop, 0d);
        below(out, "phosphore", "Phosphore", "", r.getPhosphore(),
                req.getPhosphoreMin(), null, "CARENCES_NUTRITIVES", crop, 0d);
        below(out, "potassium", "Potassium", "", r.getPotassium(),
                req.getPotassiumMin(), null, "CARENCES_NUTRITIVES", crop, 0d);
    }

    // ============================================================
    // Déficit de pression de vapeur
    // ============================================================
    private void analyzeVpd(List<AgronomicFinding> out, SensorReading r) {
        Double vpd = indicators.vaporPressureDeficit(r.getTemperature(), r.getHumiditeAir());
        if (vpd == null) return;

        if (vpd < DerivedIndicators.VPD_CONFINED) {
            double severity = clamp((DerivedIndicators.VPD_CONFINED - vpd) / DerivedIndicators.VPD_CONFINED);
            if (severity < agronomic.getMinSeverity()) return;

            out.add(build("RISQUE_MALADIE", "vpd", vpd, DerivedIndicators.VPD_CONFINED,
                    DerivedIndicators.VPD_CONFINED - vpd, severity,
                    String.format(FR,
                            "Déficit de pression de vapeur à %.2f kPa (seuil de confinement : %.2f kPa). "
                                    + "L'air est proche de la saturation à %.1f °C et %.1f %% d'humidité. "
                                    + "La transpiration est entravée et l'eau stagne sur le feuillage.",
                            vpd, DerivedIndicators.VPD_CONFINED, r.getTemperature(), r.getHumiditeAir())));

        } else if (vpd > DerivedIndicators.VPD_EXCESSIVE) {
            double severity = clamp((vpd - DerivedIndicators.VPD_EXCESSIVE) / DerivedIndicators.VPD_EXCESSIVE);
            if (severity < agronomic.getMinSeverity()) return;

            out.add(build("STRESS_HYDRIQUE", "vpd", vpd, DerivedIndicators.VPD_EXCESSIVE,
                    vpd - DerivedIndicators.VPD_EXCESSIVE, severity,
                    String.format(FR,
                            "Déficit de pression de vapeur à %.2f kPa (seuil de stress : %.2f kPa). "
                                    + "À %.1f °C et %.1f %% d'humidité, la demande évaporative dépasse "
                                    + "ce que les racines peuvent compenser, même en sol pourvu.",
                            vpd, DerivedIndicators.VPD_EXCESSIVE, r.getTemperature(), r.getHumiditeAir())));
        }
    }

    // ============================================================
    // Équilibre nutritif
    // ============================================================
    private void analyzeNutrientBalance(List<AgronomicFinding> out, SensorReading r, CropRequirement req) {
        Map<String, Double> ratios = indicators.nutrientRatios(r, req);
        Double imbalance = indicators.imbalance(ratios);
        if (imbalance == null || imbalance <= DerivedIndicators.NUTRIENT_IMBALANCE_LIMIT) return;

        String dominant = indicators.dominantNutrient(ratios);
        String limiting = indicators.limitingNutrient(ratios);

        double severity = clamp((imbalance - DerivedIndicators.NUTRIENT_IMBALANCE_LIMIT)
                / DerivedIndicators.NUTRIENT_IMBALANCE_LIMIT);

        out.add(build("CARENCES_NUTRITIVES", "equilibre_npk", imbalance,
                DerivedIndicators.NUTRIENT_IMBALANCE_LIMIT,
                imbalance - DerivedIndicators.NUTRIENT_IMBALANCE_LIMIT, severity,
                String.format(FR,
                        "Déséquilibre nutritif : %s est %.1f fois mieux pourvu que %s au regard des "
                                + "besoins de la culture. Un excès relatif entrave l'assimilation des autres "
                                + "éléments, si bien qu'une carence peut apparaître sur un sol pourtant riche.",
                        withArticle(dominant), imbalance, withArticle(limiting))));
    }

    // ============================================================
    // Interne
    // ============================================================
    /**
     * Seuils applicables, infléchis par le stade de croissance lorsqu'un
     * infléchissement est décrit pour ce stade.
     */
    private CropRequirement requirement(String cropName, String growthStage, SensorReading reading) {
        if (cropName == null || reading == null) return null;
        return requirementResolver.resolve(cropName, growthStage).orElse(null);
    }

    private void below(List<AgronomicFinding> out, String field, String label, String unit,
                       Double observed, Double min, Double max,
                       String category, String crop, double tolerance) {
        if (observed == null || min == null || observed >= min) return;

        double gap = min - observed;
        double severity = clamp((gap / amplitude(min, max)) * (1 - tolerance));
        if (severity < agronomic.getMinSeverity()) return;

        out.add(build(category, field, observed, min, gap, severity,
                String.format(FR, "%s : %.1f%s, en deçà du seuil bas de la culture %s : %.1f%s (déficit de %.1f%s).",
                        label, observed, unit, crop, min, unit, gap, unit)));
    }

    private void above(List<AgronomicFinding> out, String field, String label, String unit,
                       Double observed, Double max, Double min,
                       String category, String crop, double tolerance) {
        if (observed == null || max == null || observed <= max) return;

        double gap = observed - max;
        double severity = clamp((gap / amplitude(min, max)) * (1 - tolerance));
        if (severity < agronomic.getMinSeverity()) return;

        out.add(build(category, field, observed, max, gap, severity,
                String.format(FR, "%s : %.1f%s, au-delà du seuil haut de la culture %s : %.1f%s (excès de %.1f%s).",
                        label, observed, unit, crop, max, unit, gap, unit)));
    }

    /**
     * Référence de normalisation de la gravité.
     *
     * On rapporte l'écart à l'amplitude de la plage optimale plutôt qu'au seuil
     * lui-même : une culture à exigences étroites voit ses dépassements pesés
     * plus lourdement qu'une culture tolérante, ce qui est agronomiquement juste.
     * À défaut d'amplitude — cas des seuils nutritifs, sans maximum — on retombe
     * sur le seuil.
     */
    private double amplitude(Double min, Double max) {
        if (min != null && max != null && max > min) return max - min;
        if (max != null && max > 0) return max;
        if (min != null && min > 0) return min;
        return 1d;
    }

    private AgronomicFinding build(String category, String field, double observed,
                                   double threshold, double gap, double severity, String statement) {
        return AgronomicFinding.builder()
                .category(category)
                .measureField(field)
                .observedValue(observed)
                .thresholdValue(threshold)
                .gap(gap)
                .severityIndex(round(severity))
                .priority(priorityOf(severity))
                .statement(statement)
                .build();
    }

    /** Élide l'article devant un nom commençant par une voyelle : l'azote, le phosphore. */
    private String withArticle(String nutrient) {
        if (nutrient == null || nutrient.isBlank()) return "";
        char first = Character.toLowerCase(nutrient.charAt(0));
        return "aeiouyàâéèêëîïôöûüh".indexOf(first) >= 0
                ? "l'" + nutrient
                : "le " + nutrient;
    }

    private String priorityOf(double severity) {
        if (severity >= 0.30) return "HAUTE";
        if (severity >= 0.10) return "MOYENNE";
        return "BASSE";
    }

    private double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
