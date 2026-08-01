package com.sni.bilanga.diagnosis.service.support;

import com.sni.bilanga.diagnosis.dto.response.AlternativeComparison;
import com.sni.bilanga.diagnosis.dto.response.ClassProbability;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.DiseaseRisk;
import com.sni.bilanga.knowledge.service.interfaces.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Répond à « pourquoi cette maladie, et pas l'autre ? ».
 *
 * <p><strong>Ce que cela change.</strong> Le système affichait « mildiou 97 %,
 * alternariose 2 % » — un verdict, pas une explication. Un agronome à qui l'on
 * annonce un mildiou demande immédiatement ce qui permet d'écarter
 * l'alternariose, puisque les deux donnent des taches foliaires. Sans réponse,
 * le diagnostic reste une opinion de machine.
 *
 * <p><strong>La réponse existait déjà, en pièces détachées.</strong>
 * {@code DiagnosisResult} portait les alternatives du classifieur ;
 * {@code RiskEngine} calcule, pour chaque maladie et à partir des seules
 * mesures, la fraction des conditions d'apparition réunies. Croiser les deux
 * donne l'argument : les mêmes symptômes, mais les conditions du mildiou et
 * non celles de l'alternariose.
 *
 * <p><strong>Deux voies indépendantes.</strong> C'est ce qui fait la force de
 * l'argument : la probabilité vient d'un réseau convolutif entraîné sur des
 * images, le score de risque d'un moteur déterministe appliqué à des mesures de
 * sol. Elles n'ont aucune information en commun. Quand elles concordent, la
 * conclusion tient sur deux pieds ; quand elles divergent, le dire est plus
 * honnête que de trancher en silence.
 *
 * <p>Chaîne image uniquement : la chaîne capteur ne produit pas d'alternatives,
 * et il n'y a rien à comparer.
 */
@Component
@RequiredArgsConstructor
public class ComparativeExplainer {

    private static final Locale FR = Locale.FRANCE;

    /**
     * Au-delà, la comparaison lasse plus qu'elle n'éclaire.
     *
     * La quatrième alternative d'un classifieur est déjà sous le pour-cent : la
     * justifier donnerait l'impression d'une hésitation qui n'existe pas.
     */
    private static final int MAX_COMPARISONS = 2;

    /**
     * En deçà, l'alternative est trop marginale pour mériter d'être discutée.
     * L'évoquer suggérerait un doute que le modèle n'a pas.
     */
    private static final double MIN_PROBABILITY = 0.005;

    private final KnowledgeService knowledgeService;

    /**
     * @param retainedCode maladie conclue par le modèle
     * @param alternatives classes suivantes, par probabilité décroissante
     * @param reading      relevé ayant servi au calcul des risques ; sans lui,
     *                     aucune comparaison n'est possible
     */
    public List<AlternativeComparison> compare(String cropName, String retainedCode,
                                               Double retainedProbability,
                                               List<ClassProbability> alternatives,
                                               SensorReading reading) {

        List<AlternativeComparison> comparisons = new ArrayList<>();

        // Sans relevé, la seconde voie n'a rien à dire : il ne resterait que la
        // probabilité du modèle, qu'on afficherait deux fois sous deux noms.
        if (reading == null || cropName == null || retainedCode == null
                || alternatives == null || alternatives.isEmpty()) {
            return comparisons;
        }

        DiseaseRisk retainedRisk = knowledgeService.riskFor(cropName, retainedCode, reading);
        Set<String> retainedConditions = conditionsOf(retainedRisk);

        for (ClassProbability alternative : alternatives) {
            if (comparisons.size() >= MAX_COMPARISONS) {
                break;
            }
            if (alternative.getDiseaseCode() == null
                    || alternative.getDiseaseCode().equals(retainedCode)) {
                continue;
            }
            if (alternative.getProbability() != null
                    && alternative.getProbability() < MIN_PROBABILITY) {
                continue;
            }

            DiseaseRisk alternativeRisk =
                    knowledgeService.riskFor(cropName, alternative.getDiseaseCode(), reading);

            comparisons.add(build(retainedRisk, retainedCode, retainedProbability,
                    retainedConditions, alternative, alternativeRisk));
        }

        return comparisons;
    }

    /**
     * Comparaison reconstituée <strong>sans</strong> les probabilités du modèle.
     *
     * <p>Employée par l'explication d'un diagnostic passé. Les probabilités
     * rendues par le classifieur ne sont pas conservées en base — seul le
     * résultat retenu et sa confiance le sont. Les recalculer supposerait de
     * relancer l'inférence sur une image qu'on n'a plus, et donnerait de toute
     * façon la réponse d'aujourd'hui, pas celle du moment où le conseil a été
     * émis. C'est la règle déjà posée par {@code DiagnosisExplainer}.
     *
     * <p>Reste donc la seconde voie, celle des mesures — qui a l'avantage d'être
     * exactement reproductible depuis le relevé enregistré. Les alternatives
     * candidates sont les maladies dont les conditions étaient partiellement
     * réunies : ce sont précisément celles qu'on aurait pu confondre.
     */
    public List<AlternativeComparison> compareFromMeasurements(String cropName, String retainedCode,
                                                               SensorReading reading) {
        if (reading == null || cropName == null || retainedCode == null) {
            return List.of();
        }

        List<ClassProbability> candidates = knowledgeService.assessRisks(cropName, reading).stream()
                .filter(risk -> !retainedCode.equals(risk.getDiseaseCode()))
                // Probabilité absente, et non nulle : le modèle n'a pas dit
                // « zéro », il n'a rien dit qu'on ait conservé.
                .map(risk -> ClassProbability.builder()
                        .diseaseCode(risk.getDiseaseCode())
                        .probability(null)
                        .build())
                .toList();

        return compare(cropName, retainedCode, null, candidates, reading);
    }

    private AlternativeComparison build(DiseaseRisk retainedRisk, String retainedCode,
                                        Double retainedProbability, Set<String> retainedConditions,
                                        ClassProbability alternative, DiseaseRisk alternativeRisk) {

        Set<String> alternativeConditions = conditionsOf(alternativeRisk);

        Set<String> shared = new LinkedHashSet<>(retainedConditions);
        shared.retainAll(alternativeConditions);

        Set<String> distinguishing = new LinkedHashSet<>(retainedConditions);
        distinguishing.removeAll(alternativeConditions);

        String retainedName = displayNameOf(retainedRisk, retainedCode);
        String alternativeName = displayNameOf(alternativeRisk, alternative.getDiseaseCode());

        return AlternativeComparison.builder()
                .diseaseCode(alternative.getDiseaseCode())
                .displayName(alternativeName)
                .modelProbability(alternative.getProbability())
                .riskScore(alternativeRisk == null ? null : alternativeRisk.getRiskScore())
                .sharedConditions(List.copyOf(shared))
                .distinguishingConditions(List.copyOf(distinguishing))
                .statement(statement(retainedName, retainedProbability, retainedRisk,
                        alternativeName, alternative.getProbability(), alternativeRisk,
                        shared, distinguishing))
                .build();
    }

    /**
     * Compose l'argument.
     *
     * <p>Quatre cas, et chacun dit une chose différente. Les confondre sous une
     * formule unique donnerait une phrase toujours vraie et jamais informative.
     */
    private String statement(String retainedName, Double retainedProbability, DiseaseRisk retainedRisk,
                             String alternativeName, Double alternativeProbability,
                             DiseaseRisk alternativeRisk,
                             Set<String> shared, Set<String> distinguishing) {

        StringBuilder text = new StringBuilder();
        text.append(String.format(FR, "%s retenu%s plutôt que %s",
                retainedName,
                retainedProbability == null ? "" : String.format(FR, " (%.0f %%)", retainedProbability * 100),
                alternativeName));

        if (alternativeProbability != null) {
            text.append(String.format(FR, " (%.0f %%)", alternativeProbability * 100));
        }
        text.append(" : ");

        // Cas 1 — les mesures départagent, et c'est l'argument le plus fort :
        // deux voies indépendantes concordent.
        if (!distinguishing.isEmpty() && retainedRisk != null && alternativeRisk != null
                && retainedRisk.getRiskScore() > alternativeRisk.getRiskScore()) {

            if (!shared.isEmpty()) {
                text.append("les deux maladies partagent ")
                        .append(String.join(", ", shared))
                        .append(", mais ");
            }
            text.append("les conditions mesurées réunissent ")
                    .append(String.join(", ", distinguishing))
                    .append(String.format(FR,
                            ", ce qui correspond à %s (%.0f %% des conditions réunies) "
                                    + "et non à %s (%.0f %%).",
                            retainedName, retainedRisk.getRiskScore() * 100,
                            alternativeName, alternativeRisk.getRiskScore() * 100));
            return text.toString();
        }

        // Cas 2 — les mesures pencheraient plutôt pour l'alternative. Le dire est
        // plus utile que de le taire : c'est le cas où un œil humain doit trancher.
        if (retainedRisk != null && alternativeRisk != null
                && alternativeRisk.getRiskScore() > retainedRisk.getRiskScore()) {

            return text.append(String.format(FR,
                    "le modèle a tranché sur l'image, mais les conditions mesurées "
                            + "correspondent davantage à %s (%.0f %% contre %.0f %%). "
                            + "Un examen visuel de confirmation est recommandé avant de traiter.",
                    alternativeName, alternativeRisk.getRiskScore() * 100,
                    retainedRisk.getRiskScore() * 100)).toString();
        }

        // Cas 3 — les mesures ne départagent pas : la conclusion ne tient que sur
        // l'image, et le reconnaître vaut mieux que de laisser croire au contraire.
        if (!shared.isEmpty()) {
            return text.append("les conditions mesurées (")
                    .append(String.join(", ", shared))
                    .append(") conviennent aux deux. Le départage repose uniquement sur "
                            + "l'aspect des lésions relevé par le modèle.")
                    .toString();
        }

        // Cas 4 — aucune condition connue n'est réunie de part ni d'autre.
        return text.append("aucune des conditions d'apparition connues n'est réunie pour l'une "
                           + "ni pour l'autre. Le départage repose uniquement sur l'aspect des "
                           + "lésions relevé par le modèle.").toString();
    }

    private Set<String> conditionsOf(DiseaseRisk risk) {
        if (risk == null || risk.getSatisfiedConditions() == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(risk.getSatisfiedConditions());
    }

    private String displayNameOf(DiseaseRisk risk, String fallbackCode) {
        if (risk != null && risk.getDisplayName() != null && !risk.getDisplayName().isBlank()) {
            return risk.getDisplayName();
        }
        return fallbackCode;
    }
}
