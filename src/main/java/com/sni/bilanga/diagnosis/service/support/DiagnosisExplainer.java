package com.sni.bilanga.diagnosis.service.support;

import com.sni.bilanga.knowledge.service.support.DiseaseLabeller;
import com.sni.bilanga.diagnosis.dto.response.AlternativeComparison;
import com.sni.bilanga.diagnosis.dto.response.DiagnosisExplanation;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.model.Recommendation;
import com.sni.bilanga.enums.DiagnosticSource;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.enums.RecommendationType;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.enums.Culture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compose la justification d'un diagnostic à partir de ce qui a été enregistré
 * au moment où il a été produit.
 *
 * Rien n'est recalculé ici : tout provient des colonnes de traçabilité des
 * recommandations. Recalculer donnerait la justification d'aujourd'hui, pas
 * celle du conseil tel qu'il a été émis — et les deux divergeraient dès qu'un
 * seuil agronomique serait modifié.
 */
@Component
@RequiredArgsConstructor
public class DiagnosisExplainer {

    private static final Locale FR = Locale.FRANCE;

    /** Libellés lisibles des champs de mesure, tels que les moteurs les nomment. */
    private static final Map<String, String> MEASURE_LABELS = Map.ofEntries(
            Map.entry("temperature", "la température"),
            Map.entry("humidite_sol", "l'humidité du sol"),
            Map.entry("humidite_air", "l'humidité de l'air"),
            Map.entry("ph", "le pH"),
            Map.entry("azote", "l'azote"),
            Map.entry("phosphore", "le phosphore"),
            Map.entry("potassium", "le potassium"),
            Map.entry("luminosite", "la luminosité"),
            Map.entry("vpd", "le déficit de pression de vapeur"),
            Map.entry("npk", "l'équilibre NPK"));

    private final ConfidenceEvaluator confidenceEvaluator;
    private final DiseaseLabeller diseaseLabeller;
    private final ComparativeExplainer comparativeExplainer;

    public DiagnosisExplanation explain(Diagnostic d, List<Recommendation> recommendations) {
        SensorReading reading = d.getReading();
        DiagnosticSource source = DiagnosticSource.from(d.getSource());

        return DiagnosisExplanation.builder()
                .diagnosticId(d.getId())
                .plotId(d.getPlot().getId())
                .plotName(d.getPlot().getName())
                .cropName(Culture.canonical(d.getCropName()))
                .source(d.getSource())
                .sourceLabel(source == null ? null : source.getLabel())
                .result(d.getResult())
                .resultLabel(diseaseLabeller.labelFor(d.getCropName(), d.getResult()))
                .confidenceScore(d.getConfidenceScore())
                .confidenceLevel(confidenceEvaluator.level(d.getConfidenceScore()))
                .reliable(confidenceEvaluator.isReliable(d.getConfidenceScore()))
                .diagnosedAt(d.getDiagnosedAt())
                .modelName(d.getAiModel() == null ? null : d.getAiModel().getName())
                .readingId(reading == null ? null : reading.getId())
                .readingRecordedAt(reading == null ? null : reading.getRecordedAt())
                .measures(measuresOf(reading))
                .limitation(limitationOf(reading))
                .comparison(comparisonFor(d, reading, source))
                .recommendations(recommendations.stream().map(this::explainOne).toList())
                .build();
    }

    /**
     * Mise en regard des maladies candidates, pour un diagnostic déjà émis.
     *
     * <p>Réservée à la chaîne image : la chaîne capteur ne conclut pas à une
     * maladie parmi plusieurs, il n'y a rien à départager.
     */
    private List<AlternativeComparison> comparisonFor(Diagnostic d, SensorReading reading,
                                                      DiagnosticSource source) {
        if (source != DiagnosticSource.IMAGE || reading == null) {
            return List.of();
        }
        return comparativeExplainer.compareFromMeasurements(
                d.getCropName(), d.getResult(), reading);
    }

    private DiagnosisExplanation.ExplainedRecommendation explainOne(Recommendation r) {
        RecommendationType type = RecommendationType.from(r.getRecommendationType());
        RecommendationPriority priority = RecommendationPriority.from(r.getPriority());
        Double deviation = deviationOf(r);

        return DiagnosisExplanation.ExplainedRecommendation.builder()
                .id(r.getId())
                .content(r.getContent())
                .type(r.getRecommendationType())
                .typeLabel(type == null ? null : type.getLabel())
                .priority(r.getPriority())
                .priorityLabel(priority == null ? null : priority.getLabel())
                .status(r.getStatus())
                .sourceRuleId(r.getSourceRuleId())
                .measureField(r.getMeasureField())
                .observedValue(r.getObservedValue())
                .thresholdValue(r.getThresholdValue())
                .deviation(deviation)
                .rationale(rationaleOf(r, type, deviation))
                .build();
    }

    /**
     * Phrase de justification. Trois cas, du plus informatif au moins :
     * une mesure chiffrée confrontée à son seuil, un arbitrage entre conseils
     * contradictoires, ou l'absence de traçabilité pour les conseils issus
     * directement du modèle.
     */
    private String rationaleOf(Recommendation r, RecommendationType type, Double deviation) {
        if (type == RecommendationType.ARBITRAGE) {
            return "Synthèse ajoutée pour concilier deux conseils qui se contredisaient. "
                   + "Elle ne remplace aucun d'eux : les deux restent affichés.";
        }

        String field = r.getMeasureField();
        if (field != null && r.getObservedValue() != null && r.getThresholdValue() != null) {
            String label = MEASURE_LABELS.getOrDefault(field, field);
            String direction = deviation != null && deviation < 0 ? "en deçà du" : "au-delà du";

            return String.format(FR,
                    "Déclenché parce que %s vaut %.2f, soit %s seuil de %.2f (écart de %.2f).",
                    label, r.getObservedValue(), direction, r.getThresholdValue(),
                    deviation == null ? 0d : Math.abs(deviation));
        }

        if (field != null && r.getObservedValue() != null) {
            String label = MEASURE_LABELS.getOrDefault(field, field);
            return String.format(FR, "Déclenché à partir de %s, mesurée à %.2f.", label, r.getObservedValue());
        }

        return "Conseil issu directement de la conclusion du modèle : aucune mesure "
               + "particulière ne l'a déclenché.";
    }

    private Double deviationOf(Recommendation r) {
        if (r.getObservedValue() == null || r.getThresholdValue() == null) {
            return null;
        }
        return round(r.getObservedValue() - r.getThresholdValue());
    }

    private Map<String, Double> measuresOf(SensorReading r) {
        if (r == null) {
            return Map.of();
        }
        Map<String, Double> measures = new LinkedHashMap<>();
        putIfPresent(measures, "temperature", r.getTemperature());
        putIfPresent(measures, "humidite_sol", r.getHumiditeSol());
        putIfPresent(measures, "humidite_air", r.getHumiditeAir());
        putIfPresent(measures, "ph", r.getPh());
        putIfPresent(measures, "azote", r.getAzote());
        putIfPresent(measures, "phosphore", r.getPhosphore());
        putIfPresent(measures, "potassium", r.getPotassium());
        putIfPresent(measures, "luminosite", r.getLuminosite());
        return measures;
    }

    /**
     * Un diagnostic produit sans relevé n'a mobilisé aucun moteur agronomique :
     * ni risque, ni tendance, ni indicateur. Le résultat paraissait complet
     * alors qu'il ne reposait que sur le modèle.
     */
    private String limitationOf(SensorReading reading) {
        if (reading != null) {
            return null;
        }
        return "Aucun relevé n'était disponible : les moteurs agronomiques, de risque et de "
               + "tendance n'ont pas pu s'exécuter. Ce diagnostic ne repose que sur le modèle.";
    }

    private void putIfPresent(Map<String, Double> target, String key, Double value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
