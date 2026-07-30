package com.sni.bilanga.knowledge.service.support;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.DiseaseRisk;
import com.sni.bilanga.knowledge.model.DiseaseRiskCondition;
import com.sni.bilanga.knowledge.repository.DiseaseRiskConditionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Moteur de prédiction du risque phytosanitaire.
 *
 * Renverse le raisonnement habituel du système : au lieu de constater une
 * maladie puis d'examiner si les conditions l'aggravent, il part des seules
 * mesures pour estimer, maladie par maladie, dans quelle proportion les
 * conditions d'apparition sont réunies.
 *
 * Le système dispose ainsi d'une voie de détection indépendante des modèles
 * de vision, capable d'alerter avant qu'aucun symptôme ne soit visible.
 */
@Component
@RequiredArgsConstructor
public class RiskEngine {

    private static final Locale FR = Locale.FRANCE;

    public static final String ELEVE = "ELEVE";
    public static final String MODERE = "MODERE";
    public static final String FAIBLE = "FAIBLE";

    private final DiseaseRiskConditionRepository riskConditionRepository;

    /** Seuils de signalement. Nommé distinctement de la variable locale « risk ». */
    private final BilangaProperties.Risk riskThresholds;

    /**
     * Évalue le risque pour chaque maladie documentée de la culture.
     * Ne retient que les maladies dont le score atteint le seuil de signalement,
     * classées par risque décroissant.
     */
    public List<DiseaseRisk> evaluate(String cropName, SensorReading reading) {
        List<DiseaseRisk> risks = new ArrayList<>();
        if (cropName == null || reading == null) return risks;

        Map<String, List<DiseaseRiskCondition>> byDisease =
                riskConditionRepository.findForCrop(cropName).stream()
                        .collect(Collectors.groupingBy(DiseaseRiskCondition::getDiseaseCode));

        for (Map.Entry<String, List<DiseaseRiskCondition>> entry : byDisease.entrySet()) {
            DiseaseRisk risk = scoreDisease(entry.getKey(), entry.getValue(), reading);
            if (risk != null && risk.getRiskScore() >= riskThresholds.getMinScore()) {
                risks.add(risk);
            }
        }

        risks.sort(Comparator.comparing(DiseaseRisk::getRiskScore).reversed());
        return risks;
    }

    /** Risque pour une maladie précise, quel que soit son score. */
    public DiseaseRisk evaluateFor(String cropName, String diseaseCode, SensorReading reading) {
        if (cropName == null || diseaseCode == null || reading == null) return null;

        List<DiseaseRiskCondition> conditions =
                riskConditionRepository.findByCropNameAndDiseaseCode(cropName, diseaseCode);
        if (conditions.isEmpty()) return null;

        return scoreDisease(diseaseCode, conditions, reading);
    }

    // ============================================================
    // Interne
    // ============================================================
    private DiseaseRisk scoreDisease(String diseaseCode,
                                     List<DiseaseRiskCondition> conditions,
                                     SensorReading reading) {
        double totalWeight = 0;
        double satisfiedWeight = 0;
        List<String> satisfied = new ArrayList<>();

        for (DiseaseRiskCondition condition : conditions) {
            Double measured = extractMeasure(reading, condition.getMeasureField());
            if (measured == null) {
                continue; // mesure absente : la condition n'est ni vérifiée ni infirmée
            }

            totalWeight += condition.getWeight();
            if (matches(measured, condition)) {
                satisfiedWeight += condition.getWeight();
                satisfied.add(String.format(FR, "%s (mesuré : %.1f)", condition.getLabel(), measured));
            }
        }

        if (totalWeight == 0) return null;

        double score = round(satisfiedWeight / totalWeight);

        return DiseaseRisk.builder()
                .diseaseCode(diseaseCode)
                .riskScore(score)
                .level(levelOf(score))
                .satisfiedConditions(satisfied)
                .build();
    }

    private String levelOf(double score) {
        if (score >= riskThresholds.getHighScore()) return ELEVE;
        if (score >= riskThresholds.getMinScore()) return MODERE;
        return FAIBLE;
    }

    private Double extractMeasure(SensorReading r, String field) {
        return switch (field) {
            case "temperature"  -> r.getTemperature();
            case "humidite_sol" -> r.getHumiditeSol();
            case "humidite_air" -> r.getHumiditeAir();
            case "ph"           -> r.getPh();
            case "azote"        -> r.getAzote();
            case "phosphore"    -> r.getPhosphore();
            case "potassium"    -> r.getPotassium();
            case "luminosite"   -> r.getLuminosite();
            default -> null;
        };
    }

    private boolean matches(double value, DiseaseRiskCondition c) {
        double low = c.getThreshold();
        Double high = c.getThresholdMax();

        return switch (c.getOperator()) {
            case ">"       -> value > low;
            case "<"       -> value < low;
            case ">="      -> value >= low;
            case "<="      -> value <= low;
            case "=="      -> value == low;
            case "BETWEEN" -> high != null && value >= low && value <= high;
            default        -> false;
        };
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}