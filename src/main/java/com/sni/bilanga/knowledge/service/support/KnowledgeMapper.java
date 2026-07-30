package com.sni.bilanga.knowledge.service.support;

import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.GrowthStage;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.knowledge.dto.response.*;
import com.sni.bilanga.knowledge.model.*;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Vues de lecture des tables de connaissance.
 *
 * Les contrôleurs d'administration renvoyaient les entités JPA telles quelles,
 * ce qui liait le contrat d'API au schéma et exposait jusqu'à la colonne de
 * version ajoutée en V12. Le passage par des DTO ajoute au passage ce que
 * l'entité ne porte pas : libellés lisibles et formulation des conditions.
 */
@Component
public class KnowledgeMapper {

    private static final Locale FR = Locale.FRANCE;

    /** Joker signifiant « quelle que soit la culture », employé par les règles. */
    private static final String ANY_CROP = "*";

    private static final Map<String, String> MEASURE_LABELS = Map.ofEntries(
            Map.entry("temperature", "température"),
            Map.entry("humidite_sol", "humidité du sol"),
            Map.entry("humidite_air", "humidité de l'air"),
            Map.entry("ph", "pH"),
            Map.entry("azote", "azote"),
            Map.entry("phosphore", "phosphore"),
            Map.entry("potassium", "potassium"),
            Map.entry("luminosite", "luminosité"));

    public CropRequirementResponse toResponse(CropRequirement r) {
        return CropRequirementResponse.builder()
                .id(r.getId())
                .cropName(Culture.canonical(r.getCropName()))
                .phMin(r.getPhMin()).phMax(r.getPhMax())
                .humSolMin(r.getHumSolMin()).humSolMax(r.getHumSolMax())
                .tempMin(r.getTempMin()).tempMax(r.getTempMax())
                .azoteMin(r.getAzoteMin())
                .phosphoreMin(r.getPhosphoreMin())
                .potassiumMin(r.getPotassiumMin())
                .toleranceSecheresse(r.getToleranceSecheresse())
                .build();
    }

    public CropStageRequirementResponse toResponse(CropStageRequirement r) {
        GrowthStage stage = GrowthStage.from(r.getGrowthStage());

        return CropStageRequirementResponse.builder()
                .id(r.getId())
                .cropName(Culture.canonical(r.getCropName()))
                .growthStage(r.getGrowthStage())
                .growthStageLabel(stage == null ? null : stage.getLabel())
                .label(r.getLabel())
                .phMin(r.getPhMin()).phMax(r.getPhMax())
                .humSolMin(r.getHumSolMin()).humSolMax(r.getHumSolMax())
                .tempMin(r.getTempMin()).tempMax(r.getTempMax())
                .azoteMin(r.getAzoteMin())
                .phosphoreMin(r.getPhosphoreMin())
                .potassiumMin(r.getPotassiumMin())
                .toleranceSecheresse(r.getToleranceSecheresse())
                .build();
    }

    public DiseaseKnowledgeResponse toResponse(DiseaseKnowledge d) {
        return DiseaseKnowledgeResponse.builder()
                .id(d.getId())
                .cropName(Culture.canonical(d.getCropName()))
                .diseaseCode(d.getDiseaseCode())
                .displayName(d.getDisplayName())
                .symptoms(d.getSymptoms())
                .favorableConditions(d.getFavorableConditions())
                .treatment(d.getTreatment())
                .prevention(d.getPrevention())
                .priority(d.getPriority())
                .priorityLabel(priorityLabel(d.getPriority()))
                .estimatedCost(d.getEstimatedCost())
                .build();
    }

    public DiseaseRiskConditionResponse toResponse(DiseaseRiskCondition c) {
        return DiseaseRiskConditionResponse.builder()
                .id(c.getId())
                .cropName(Culture.canonical(c.getCropName()))
                .diseaseCode(c.getDiseaseCode())
                .measureField(c.getMeasureField())
                .measureLabel(measureLabel(c.getMeasureField()))
                .operator(c.getOperator())
                .threshold(c.getThreshold())
                .thresholdMax(c.getThresholdMax())
                .weight(c.getWeight())
                .label(c.getLabel())
                .active(c.getActive())
                .expression(expression(c.getMeasureField(), c.getOperator(),
                        c.getThreshold(), c.getThresholdMax()))
                .build();
    }

    public KnowledgeRuleResponse toResponse(KnowledgeRule r) {
        return KnowledgeRuleResponse.builder()
                .id(r.getId())
                .category(r.getCategory())
                .cropName(Culture.canonical(r.getCropName()))
                .cropAgnostic(ANY_CROP.equals(r.getCropName()))
                .conditionText(r.getConditionText())
                .proposedAction(r.getProposedAction())
                .priority(r.getPriority())
                .priorityLabel(priorityLabel(r.getPriority()))
                .validated(r.getValidated())
                .estimatedCost(r.getEstimatedCost())
                .build();
    }

    public CorrelationRuleResponse toResponse(CorrelationRule c) {
        return CorrelationRuleResponse.builder()
                .id(c.getId())
                .cropName(Culture.canonical(c.getCropName()))
                .diseaseCode(c.getDiseaseCode())
                .measureField(c.getMeasureField())
                .measureLabel(measureLabel(c.getMeasureField()))
                .operator(c.getOperator())
                .threshold(c.getThreshold())
                .extraRecommendation(c.getExtraRecommendation())
                .priority(c.getPriority())
                .priorityLabel(priorityLabel(c.getPriority()))
                .expression(expression(c.getMeasureField(), c.getOperator(), c.getThreshold(), null))
                .build();
    }

    public ArbitrationResponse toResponse(RecommendationArbitration a) {
        return ArbitrationResponse.builder()
                .id(a.getId())
                .cropName(Culture.canonical(a.getCropName()))
                .categoryA(a.getCategoryA())
                .categoryB(a.getCategoryB())
                .synthesis(a.getSynthesis())
                .priority(a.getPriority())
                .priorityLabel(priorityLabel(a.getPriority()))
                .active(a.getActive())
                .build();
    }

    // ============================================================
    // Interne
    // ============================================================

    /**
     * Rend la condition lisible sans avoir à recomposer opérateur et bornes
     * côté client — et rend immédiatement visible une règle mal saisie, par
     * exemple un {@code BETWEEN} dont la borne haute manque.
     */
    private String expression(String field, String operator, Double threshold, Double thresholdMax) {
        if (field == null || operator == null || threshold == null) {
            return null;
        }
        String label = measureLabel(field);

        if ("BETWEEN".equalsIgnoreCase(operator)) {
            return thresholdMax == null
                    ? String.format(FR, "%s entre %.2f et … (borne haute manquante)", label, threshold)
                    : String.format(FR, "%s entre %.2f et %.2f", label, threshold, thresholdMax);
        }
        return String.format(FR, "%s %s %.2f", label, operator, threshold);
    }

    private String measureLabel(String field) {
        return field == null ? null : MEASURE_LABELS.getOrDefault(field, field);
    }

    private String priorityLabel(String priority) {
        RecommendationPriority parsed = RecommendationPriority.from(priority);
        return parsed == null ? null : parsed.getLabel();
    }
}
