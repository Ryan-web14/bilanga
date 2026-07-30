package com.sni.bilanga.diagnosis.service.support;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.dto.response.ClassProbability;
import com.sni.bilanga.knowledge.service.interfaces.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ConfidenceEvaluator {

    public static final String ELEVEE = "ELEVEE";
    public static final String MOYENNE = "MOYENNE";
    public static final String FAIBLE = "FAIBLE";

    private final KnowledgeService knowledgeService;

    /** Seuils de fiabilité. Nommé distinctement du paramètre « confidence ». */
    private final BilangaProperties.Confidence thresholds;

    public String level(Double confidence) {
        if (confidence == null) return FAIBLE;
        if (confidence >= thresholds.getHigh()) return ELEVEE;
        if (confidence >= thresholds.getLow()) return MOYENNE;
        return FAIBLE;
    }

    public boolean isReliable(Double confidence) {
        return confidence != null && confidence >= thresholds.getLow();
    }

    /** Les n classes les plus probables, codes normalisés, ordre décroissant. */
    public List<ClassProbability> topClasses(Map<String, Double> probabilities, int n) {
        if (probabilities == null || probabilities.isEmpty()) return List.of();

        return probabilities.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .map(e -> ClassProbability.builder()
                        .diseaseCode(knowledgeService.normalizeDiseaseCode(e.getKey()))
                        .probability(e.getValue())
                        .build())
                .toList();
    }

    /** Message destiné à l'utilisateur, null lorsque la prédiction est fiable. */
    public String advisoryForImage(Double confidence, List<ClassProbability> top) {
        if (isReliable(confidence)) return null;

        String alternatives = top.stream()
                .limit(2)
                .map(ClassProbability::getDiseaseCode)
                .reduce((a, b) -> a + " ou " + b)
                .orElse("plusieurs maladies");

        return String.format(Locale.FRANCE,
                "Confiance faible (%.0f %%) : le modèle hésite entre %s. "
                        + "Reprenez une photographie du symptôme en lumière naturelle, cadrée sur la feuille atteinte, "
                        + "et faites confirmer par un technicien avant tout traitement.",
                confidence == null ? 0d : confidence * 100, alternatives);
    }

    /** Le modèle tabulaire n'expose pas de distribution : message plus sobre. */
    public String advisoryForSensor(Double confidence) {
        if (isReliable(confidence)) return null;

        return String.format(Locale.FRANCE,
                "Confiance faible (%.0f %%) sur le diagnostic issu des capteurs. "
                        + "Les constats agronomiques chiffrés ci-dessous restent fiables : "
                        + "ils découlent directement des seuils de la culture, sans passer par un modèle.",
                confidence == null ? 0d : confidence * 100);
    }

    private Comparator<ClassProbability> byProbabilityDesc() {
        return Comparator.comparing(ClassProbability::getProbability).reversed();
    }
}