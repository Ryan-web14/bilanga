package com.sni.bilanga.knowledge.service.support;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.enums.ConfidenceLevel;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.SensorReadingRepository;
import com.sni.bilanga.knowledge.dto.response.TrendFinding;
import com.sni.bilanga.knowledge.model.CropRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Analyse l'évolution des mesures et annonce un franchissement de seuil
 * avant qu'il ne survienne.
 *
 * Les autres moteurs raisonnent sur un instantané : ils constatent qu'un seuil
 * est franchi. Celui-ci lit la pente et projette. Une humidité qui décroît
 * régulièrement annonce un stress hydrique plusieurs heures avant que la mesure
 * ne devienne mauvaise — c'est le passage du diagnostic à l'anticipation, et la
 * différence entre irriguer à temps et constater les dégâts.
 *
 * <p><strong>Une pente n'est publiée que si la droite explique réellement les
 * mesures</strong> (coefficient de détermination R²). Une série erratique
 * produit toujours une pente — la régression n'échoue jamais — et sans ce
 * contrôle elle donnait lieu à une projection présentée comme fiable. Annoncer
 * un stress hydrique dans quatre heures sur la foi du bruit de mesure fait
 * perdre la confiance de l'exploitant plus vite que ne rien annoncer.
 */
@Component
@RequiredArgsConstructor
public class TrendAnalyzer {

    private static final Locale FR = Locale.FRANCE;

    private final SensorReadingRepository sensorReadingRepository;
    private final CropRequirementResolver requirementResolver;
    private final BilangaProperties.Trend trendConfig;







    public List<TrendFinding> analyze(String cropName, String growthStage, Long plotId) {
        List<TrendFinding> findings = new ArrayList<>();
        if (cropName == null || plotId == null) return findings;

        // La projection vise les seuils du stade en cours : un même rythme de
        // baisse n'a pas la même portée en levée qu'en maturation.
        CropRequirement req = requirementResolver.resolve(cropName, growthStage).orElse(null);
        if (req == null) return findings;

        List<SensorReading> window = recentWindow(plotId);
        if (window.size() < trendConfig.getMinPoints()) return findings;

        addTrend(findings, window, "humidite_sol", "Humidité du sol", " %",
                SensorReading::getHumiditeSol, req.getHumSolMin(), req.getHumSolMax());
        addTrend(findings, window, "humidite_air", "Humidité de l'air", " %",
                SensorReading::getHumiditeAir, null, null);
        addTrend(findings, window, "temperature", "Température", " °C",
                SensorReading::getTemperature, req.getTempMin(), req.getTempMax());
        addTrend(findings, window, "ph", "pH du sol", "",
                SensorReading::getPh, req.getPhMin(), req.getPhMax());

        // Les nutriments et la luminosité étaient laissés de côté alors que la
        // base porte leurs seuils : un azote qui s'épuise régulièrement se voit
        // à la pente bien avant de passer sous le minimum requis.
        addTrend(findings, window, "azote", "Azote", " mg/kg",
                SensorReading::getAzote, req.getAzoteMin(), null);
        addTrend(findings, window, "phosphore", "Phosphore", " mg/kg",
                SensorReading::getPhosphore, req.getPhosphoreMin(), null);
        addTrend(findings, window, "potassium", "Potassium", " mg/kg",
                SensorReading::getPotassium, req.getPotassiumMin(), null);
        addTrend(findings, window, "luminosite", "Luminosité", " lux",
                SensorReading::getLuminosite, null, null);

        return findings;
    }

    // ============================================================
    // Fenêtre d'observation
    // ============================================================
    private List<SensorReading> recentWindow(Long plotId) {
        Instant since = Instant.now().minus(Duration.ofHours(trendConfig.getWindowHours()));

        return sensorReadingRepository
                .findByPlot_IdOrderByRecordedAtDesc(plotId, PageRequest.of(0, trendConfig.getMaxReadings()))
                .stream()
                .filter(r -> r.getRecordedAt() != null && r.getRecordedAt().isAfter(since))
                .toList();
    }

    // ============================================================
    // Régression et projection
    // ============================================================
    private void addTrend(List<TrendFinding> out, List<SensorReading> window,
                          String field, String label, String unit,
                          Function<SensorReading, Double> accessor,
                          Double min, Double max) {

        List<SensorReading> usable = window.stream()
                .filter(r -> accessor.apply(r) != null)
                .toList();

        if (usable.size() < trendConfig.getMinPoints()) return;

        // La fenêtre arrive du plus récent au plus ancien : la valeur courante
        // est en tête de liste.
        double current = accessor.apply(usable.getFirst());
        Regression regression = regress(usable, accessor);

        if (regression == null || isNegligible(regression, usable, accessor)) return;

        // Une droite qui n'explique pas les mesures ne permet pas de projeter.
        if (regression.rSquared() < trendConfig.getMinRSquared()) return;

        // Une mesure déjà hors plage relève du constat, pas de l'anticipation :
        // le moteur agronomique la signale déjà.
        if (min != null && current < min) return;
        if (max != null && current > max) return;

        double slope = regression.slope();
        Double target = null;
        if (slope < 0 && min != null) target = min;
        if (slope > 0 && max != null) target = max;
        if (target == null) return;

        double hours = (target - current) / slope;
        if (hours <= 0 || hours > trendConfig.getHorizonHours()) return;

        out.add(TrendFinding.builder()
                .measureField(field)
                .slopePerHour(round(slope, 2))
                .currentValue(round(current, 1))
                .thresholdValue(target)
                .hoursToThreshold(round(hours, 1))
                .sampleSize(usable.size())
                .rSquared(round(regression.rSquared(), 2))
                .fitQuality(fitQuality(regression.rSquared()))
                .priority(hours <= 2
                        ? RecommendationPriority.HAUTE.name()
                        : RecommendationPriority.MOYENNE.name())
                .statement(String.format(FR,
                        "%s en %s de %.2f%s par heure, actuellement à %.1f%s. "
                                + "Au rythme observé sur %d relevés, le seuil de %.1f%s serait atteint "
                                + "dans %s. Régularité de l'évolution : %s (R² = %.2f).",
                        label, slope < 0 ? "baisse" : "hausse", Math.abs(slope), unit,
                        current, unit, usable.size(), target, unit, humanDelay(hours),
                        fitQualityLabel(regression.rSquared()), regression.rSquared()))
                .build());
    }

    /** Pente en unités par heure, et part de variation qu'elle explique. */
    private record Regression(double slope, double rSquared) { }

    /**
     * Régression par la méthode des moindres carrés, en unités par heure.
     * L'origine des temps est le relevé le plus ancien de la fenêtre.
     *
     * <p>Le R² est calculé dans la foulée : {@code 1 − SSres/SStot}. Une série
     * parfaitement plate a une variation totale nulle ; il n'y a alors rien à
     * expliquer, et la tendance n'a pas lieu d'être publiée.
     */
    private Regression regress(List<SensorReading> readings, Function<SensorReading, Double> accessor) {
        Instant origin = readings.getLast().getRecordedAt();
        int n = readings.size();

        double[] xs = new double[n];
        double[] ys = new double[n];
        double sumX = 0, sumY = 0;

        for (int i = 0; i < n; i++) {
            SensorReading r = readings.get(i);
            xs[i] = Duration.between(origin, r.getRecordedAt()).toMillis() / 3_600_000.0;
            ys[i] = accessor.apply(r);
            sumX += xs[i];
            sumY += ys[i];
        }

        double meanX = sumX / n;
        double meanY = sumY / n;

        double covariance = 0, varianceX = 0;
        for (int i = 0; i < n; i++) {
            covariance += (xs[i] - meanX) * (ys[i] - meanY);
            varianceX += (xs[i] - meanX) * (xs[i] - meanX);
        }

        // Tous les relevés portent le même horodatage : aucune pente définissable.
        if (varianceX == 0) return null;

        double slope = covariance / varianceX;
        double intercept = meanY - slope * meanX;

        double residualSum = 0, totalSum = 0;
        for (int i = 0; i < n; i++) {
            double predicted = slope * xs[i] + intercept;
            residualSum += Math.pow(ys[i] - predicted, 2);
            totalSum += Math.pow(ys[i] - meanY, 2);
        }

        // Mesures rigoureusement constantes : rien n'évolue, rien à annoncer.
        if (totalSum == 0) return null;

        return new Regression(slope, Math.clamp(1 - (residualSum / totalSum), 0d, 1d));
    }

    /**
     * Pente trop faible au regard de l'amplitude observée pour signifier quoi que
     * ce soit. Comparer la pente à zéro ne servait à rien : sur des mesures
     * réelles elle n'est jamais rigoureusement nulle.
     */
    private boolean isNegligible(Regression regression, List<SensorReading> readings,
                                 Function<SensorReading, Double> accessor) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        for (SensorReading r : readings) {
            double value = accessor.apply(r);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        double amplitude = max - min;
        if (amplitude <= 0) return true;

        return Math.abs(regression.slope()) < amplitude * trendConfig.getNegligibleSlopeRatio();
    }

    private String fitQuality(double rSquared) {
        if (rSquared >= 0.85) return ConfidenceLevel.ELEVEE.name();
        return rSquared >= 0.65 ? ConfidenceLevel.MOYENNE.name() : ConfidenceLevel.FAIBLE.name();
    }

    private String fitQualityLabel(double rSquared) {
        if (rSquared >= 0.85) return "très régulière";
        return rSquared >= 0.65 ? "assez régulière" : "irrégulière";
    }

    private String humanDelay(double hours) {
        if (hours < 1) return String.format(FR, "environ %.0f minutes", hours * 60);
        long h = (long) hours;
        long m = Math.round((hours - h) * 60);
        return m == 0
                ? String.format(FR, "environ %d heure%s", h, h > 1 ? "s" : "")
                : String.format(FR, "environ %d h %02d", h, m);
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
