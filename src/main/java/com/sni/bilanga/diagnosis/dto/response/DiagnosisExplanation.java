package com.sni.bilanga.diagnosis.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Justification détaillée d'un diagnostic déjà produit.
 *
 * La migration V9 avait ajouté aux recommandations les colonnes qui disent
 * <em>pourquoi</em> chacune a été émise — règle d'origine, mesure concernée,
 * valeur observée, seuil franchi. Elles étaient écrites à chaque diagnostic et
 * n'étaient exposées nulle part : l'exploitant recevait un conseil sans jamais
 * pouvoir savoir sur quoi il reposait.
 *
 * <p>C'est ce que cette réponse restitue. Un système qui conseille sans se
 * justifier demande qu'on lui fasse confiance ; un système qui montre la mesure,
 * le seuil et l'écart laisse l'agriculteur juger par lui-même — et repérer une
 * sonde qui dérive avant de suivre un conseil fondé sur elle.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DiagnosisExplanation {

    private Long diagnosticId;
    private Long plotId;
    private String plotName;
    private String cropName;

    private String source;
    private String sourceLabel;

    private String result;
    private Double confidenceScore;
    private String confidenceLevel;

    /** Faux si la confiance est sous le seuil : le diagnostic n'a alors pas levé d'alerte. */
    private Boolean reliable;

    private Instant diagnosedAt;
    private String modelName;

    /** Relevé sur lequel le raisonnement s'est appuyé, ou {@code null} s'il n'y en avait pas. */
    private Long readingId;
    private Instant readingRecordedAt;

    /** Mesures effectivement utilisées, pour que l'écart au seuil soit vérifiable. */
    private Map<String, Double> measures;

    /**
     * Signalé quand le diagnostic a été produit sans relevé : les moteurs
     * agronomiques n'ont alors rien pu analyser, ce que le résultat ne disait pas.
     */
    private String limitation;

    /**
     * Pourquoi cette maladie plutôt que les autres candidates.
     *
     * <p>Reconstituée depuis le <strong>relevé enregistré</strong>, jamais en
     * relançant l'inférence : les probabilités du classifieur ne sont pas
     * conservées, et les recalculer donnerait la réponse d'aujourd'hui, pas
     * celle du moment où le conseil a été émis. La comparaison porte donc sur
     * les conditions d'apparition mesurées — exactement reproductibles, ce qui
     * est précisément ce qu'on attend d'une justification a posteriori.
     *
     * <p>Vide sur un diagnostic capteur, ou produit sans relevé.
     */
    private List<AlternativeComparison> comparison;

    private List<ExplainedRecommendation> recommendations;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class ExplainedRecommendation {

        private Long id;
        private String content;

        private String type;
        private String typeLabel;

        private String priority;
        private String priorityLabel;

        private String status;

        /** Identifiant de la règle de la base de connaissance ayant produit le conseil. */
        private Long sourceRuleId;

        private String measureField;
        private Double observedValue;
        private Double thresholdValue;

        /** Écart signé entre la mesure et le seuil ; {@code null} si non applicable. */
        private Double deviation;

        /** Phrase de justification, composée à partir des colonnes de traçabilité. */
        private String rationale;
    }
}
