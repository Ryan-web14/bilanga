package com.sni.bilanga.knowledge.service.implementation;


import com.sni.bilanga.knowledge.service.support.NeighbourhoodEngine;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.*;
import com.sni.bilanga.knowledge.model.DiseaseKnowledge;
import com.sni.bilanga.knowledge.model.KnowledgeRule;
import com.sni.bilanga.knowledge.repository.DiseaseKnowledgeRepository;
import com.sni.bilanga.knowledge.repository.KnowledgeRuleRepository;
import com.sni.bilanga.knowledge.service.interfaces.KnowledgeService;
import com.sni.bilanga.knowledge.service.support.*;
import com.sni.bilanga.enums.RecommendationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Locale FR = Locale.FRANCE;

    /** Catégorie attribuée aux conseils issus d'un diagnostic de maladie foliaire. */
    private static final String CATEGORY_FOLIAR_DISEASE = "MALADIE_FOLIAIRE";

    private final KnowledgeRuleRepository knowledgeRuleRepository;
    private final DiseaseKnowledgeRepository diseaseKnowledgeRepository;
    private final CorrelationEngine correlationEngine;
    private final AgronomicEngine agronomicEngine;
    private final RiskEngine riskEngine;
    private final TrendAnalyzer trendAnalyzer;
    private final ConflictArbitrator conflictArbitrator;
    private final IrrigationAdapter irrigationAdapter;
    private final WeatherEngine weatherEngine;
    private final NeighbourhoodEngine neighbourhoodEngine;

    // ============================================================
    // Chaîne image
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public List<RecommendationItem> recommendForDisease(String rawDiseaseClass,
                                                        String cropName,
                                                        SensorReading reading) {
        List<RecommendationItem> items = new ArrayList<>();
        String code = normalizeDiseaseCode(rawDiseaseClass);

        diseaseKnowledgeRepository
                .findByCropNameAndDiseaseCode(cropName, code)
                .ifPresent(dk -> items.add(RecommendationItem.builder()
                        .content(dk.getTreatment())
                        .type(RecommendationType.BASE.name())
                        .priority(dk.getPriority())
                        .category(CATEGORY_FOLIAR_DISEASE)
                        .sourceRuleId(dk.getId())
                        .estimatedCost(dk.getEstimatedCost())
                        .build()));

        items.addAll(correlationEngine.evaluate(cropName, code, reading));
        return items;
    }

    // ============================================================
    // Chaîne capteurs
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public List<RecommendationItem> recommendForSensorDiagnostic(String category, String cropName) {
        List<RecommendationItem> items = new ArrayList<>();
        String crop = (cropName == null) ? "*" : cropName;

        for (KnowledgeRule rule : knowledgeRuleRepository.findForDiagnostic(category, crop)) {
            items.add(RecommendationItem.builder()
                    .content(rule.getProposedAction())
                    .type(RecommendationType.BASE.name())
                    .priority(rule.getPriority())
                    .category(rule.getCategory())
                    .sourceRuleId(rule.getId())
                    .estimatedCost(rule.getEstimatedCost())
                    .build());
        }
        return items;
    }

    // ============================================================
    // Voie agronomique
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public List<RecommendationItem> analyzeAgronomic(String cropName, String growthStage,
                                                     SensorReading reading) {
        List<RecommendationItem> items = new ArrayList<>();

        for (AgronomicFinding finding : agronomicEngine.analyze(cropName, growthStage, reading)) {
            String content = finding.getStatement();

            List<KnowledgeRule> rules =
                    knowledgeRuleRepository.findForDiagnostic(finding.getCategory(), cropName);
            Long ruleId = null;
            java.math.BigDecimal cost = null;
            if (!rules.isEmpty()) {
                content = content + " " + rules.getFirst().getProposedAction();
                ruleId = rules.getFirst().getId();
                // Le coût suit la règle qui a fourni l'action, non le constat
                // agronomique : c'est l'action qui se paie, pas l'écart mesuré.
                cost = rules.getFirst().getEstimatedCost();
            }

            items.add(RecommendationItem.builder()
                    .content(content)
                    .type(RecommendationType.AGRONOMIQUE.name())
                    .priority(finding.getPriority())
                    .category(finding.getCategory())
                    .sourceRuleId(ruleId)
                    .estimatedCost(cost)
                    .measureField(finding.getMeasureField())
                    .observedValue(finding.getObservedValue())
                    .thresholdValue(finding.getThresholdValue())
                    .build());
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicatorSet indicators(String cropName, String growthStage, SensorReading reading) {
        return agronomicEngine.indicators(cropName, growthStage, reading);
    }

    // ============================================================
    // Voie prédictive
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public List<DiseaseRisk> assessRisks(String cropName, SensorReading reading) {
        return riskEngine.evaluate(cropName, reading).stream()
                .map(risk -> enrich(risk, cropName))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DiseaseRisk riskFor(String cropName, String diseaseCode, SensorReading reading) {
        DiseaseRisk risk = riskEngine.evaluateFor(cropName, diseaseCode, reading);
        return risk == null ? null : enrich(risk, cropName);
    }

    @Override
    public List<RecommendationItem> recommendForRisks(List<DiseaseRisk> risks) {
        List<RecommendationItem> items = new ArrayList<>();

        for (DiseaseRisk risk : risks) {
            String content = risk.getStatement();
            if (risk.getPrevention() != null && !risk.getPrevention().isBlank()) {
                content = content + " Mesures préventives : " + risk.getPrevention();
            }

            items.add(RecommendationItem.builder()
                    .content(content)
                    .type(RecommendationType.RISQUE.name())
                    .priority(RiskEngine.ELEVE.equals(risk.getLevel()) ? "HAUTE" : "MOYENNE")
                    .category("RISQUE_MALADIE")
                    .build());
        }
        return items;
    }

    // ============================================================
    // Voie anticipative
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public List<TrendFinding> assessTrends(String cropName, String growthStage, Long plotId) {
        return trendAnalyzer.analyze(cropName, growthStage, plotId);
    }

    @Override
    public List<RecommendationItem> recommendForTrends(List<TrendFinding> trends) {
        List<RecommendationItem> items = new ArrayList<>();

        for (TrendFinding trend : trends) {
            items.add(RecommendationItem.builder()
                    .content(trend.getStatement()
                            + " Intervenir avant ce terme évite d'avoir à corriger une situation installée.")
                    .type(RecommendationType.TENDANCE.name())
                    .priority(trend.getPriority())
                    .category(categoryFor(trend))
                    .measureField(trend.getMeasureField())
                    .observedValue(trend.getCurrentValue())
                    .thresholdValue(trend.getThresholdValue())
                    .build());
        }
        return items;
    }

    /**
     * Rattache la tendance au domaine correspondant, pour que l'arbitrage
     * puisse la concilier avec les autres conseils.
     */
    private String categoryFor(TrendFinding trend) {
        boolean falling = trend.getSlopePerHour() != null && trend.getSlopePerHour() < 0;

        return switch (trend.getMeasureField()) {
            case "humidite_sol" -> falling ? "STRESS_HYDRIQUE" : "EXCES_EAU";
            case "ph"           -> falling ? "SOL_ACIDE" : "SOL_ALCALIN";
            case "temperature"  -> "STRESS_THERMIQUE";
            default             -> "RISQUE_MALADIE";
        };
    }

    // ============================================================
    // Voie météorologique
    // ============================================================
    @Override
    public List<RecommendationItem> assessWeather(Plot plot) {
        return weatherEngine.evaluate(plot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecommendationItem> assessNeighbourhood(Plot plot,
                                                        List<DiseaseRisk> localRisks) {
        // Les maladies déjà signalées par les conditions locales sont écartées : le
        // voisinage ne fait que renforcer un risque que l'exploitant peut déjà
        // constater chez lui, et deux conseils pour le même problème font douter du
        // système plutôt que de la maladie.
        java.util.Set<String> localCodes = localRisks == null
                ? java.util.Set.of()
                : localRisks.stream()
                        .map(DiseaseRisk::getDiseaseCode)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());

        return neighbourhoodEngine.assess(plot, localCodes);
    }

    // ============================================================
    // Arbitrage
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public List<RecommendationItem> arbitrate(String cropName, List<RecommendationItem> items) {
        return conflictArbitrator.arbitrate(cropName, items);
    }

    // ============================================================
    // Adaptation à la parcelle
    // ============================================================
    @Override
    public List<RecommendationItem> adaptToPlot(Plot plot, List<RecommendationItem> items) {
        return irrigationAdapter.adapt(plot, items);
    }

    /**
     * Complète un risque brut avec le nom d'usage de la maladie et ses mesures
     * de prévention, puis compose l'énoncé destiné à l'utilisateur.
     */
    private DiseaseRisk enrich(DiseaseRisk risk, String cropName) {
        Optional<DiseaseKnowledge> knowledge =
                diseaseKnowledgeRepository.findByCropNameAndDiseaseCode(cropName, risk.getDiseaseCode());

        String displayName = knowledge.map(DiseaseKnowledge::getDisplayName).orElse(risk.getDiseaseCode());
        String prevention = knowledge.map(DiseaseKnowledge::getPrevention).orElse(null);

        String conditions = String.join(", ", risk.getSatisfiedConditions());
        String statement = String.format(FR,
                "Risque %s pour %s : conditions d'apparition réunies à %.0f %% (%s). "
                        + "Aucun symptôme n'est nécessairement visible à ce stade.",
                risk.getLevel().toLowerCase(FR), displayName, risk.getRiskScore() * 100, conditions);

        risk.setDisplayName(displayName);
        risk.setPrevention(prevention);
        risk.setStatement(statement);
        return risk;
    }

    // "Tomato___Late_blight" -> "Late_blight" (coupe au dernier "___")
    @Override
    public String normalizeDiseaseCode(String rawClass) {
        if (rawClass == null) return null;
        int idx = rawClass.lastIndexOf("___");
        return (idx >= 0) ? rawClass.substring(idx + 3) : rawClass;
    }
}
