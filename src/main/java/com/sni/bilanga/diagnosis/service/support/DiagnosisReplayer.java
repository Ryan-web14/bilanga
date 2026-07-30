package com.sni.bilanga.diagnosis.service.support;

import com.sni.bilanga.diagnosis.dto.response.DiagnosisReplay;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.model.Recommendation;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.DiseaseRisk;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.knowledge.dto.response.TrendFinding;
import com.sni.bilanga.knowledge.service.interfaces.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Rejoue un diagnostic passé avec les seuils <strong>actuels</strong>, et diffe.
 *
 * <h2>Le manque comblé</h2>
 *
 * <p>La base de connaissance est pilotable par API : un agronome ajuste un seuil
 * d'humidité de 35 à 32 %. Mais rien ne lui disait <em>ce que cela change</em>. Il
 * modifiait, puis attendait le prochain relevé pour voir — sur des conditions
 * différentes de celles qui l'avaient interrogé, donc sans réponse à sa question.
 *
 * <p>Le rejeu rend la connaissance <strong>expérimentable</strong> : « qu'aurait
 * dit le système, sur ce relevé précis, si ce seuil avait été à 32 % ? ». Rien n'a
 * eu besoin d'être ajouté — le relevé est en base, le diagnostic aussi, et les
 * recommandations portent leurs colonnes de traçabilité depuis la V9.
 *
 * <h2>Trois limites, toutes assumées et dites</h2>
 *
 * <p><strong>1. Rien n'est écrit.</strong> Aucun diagnostic, aucune
 * recommandation, aucune alerte. Une simulation qui laisserait des traces
 * polluerait l'historique de la parcelle avec des situations qui n'ont pas eu lieu
 * — puis les compterait dans le taux de suivi des conseils, faussant précisément
 * l'indicateur qui sert à réviser les règles.
 *
 * <p><strong>2. Le modèle d'image n'est pas rappelé.</strong> Sur un diagnostic
 * {@code IMAGE}, la photo n'est pas conservée. Le rejeu part donc du résultat
 * retenu à l'époque et rejoue les <em>moteurs</em> sur le relevé — c'est la moitié
 * du pipeline qui dépend de la base de connaissance, donc exactement celle qu'on
 * cherche à éprouver. Rappeler le modèle donnerait de surcroît la réponse
 * d'aujourd'hui d'un réseau qui a peut-être été réentraîné, ce qui mêlerait deux
 * variables.
 *
 * <p><strong>3. Il ne dit pas qui a raison.</strong> Un écart constate que la
 * connaissance a changé ; il ne dit pas qu'elle a changé en mieux. C'est à
 * l'agronome de trancher, et {@code limitation} le rappelle sur chaque réponse.
 *
 * <p>Sans état ni transaction, comme les autres classes de {@code service/support}.
 */
@Component
@RequiredArgsConstructor
public class DiagnosisReplayer {

    private static final Locale FR = Locale.FRANCE;

    private static final String KIND_ADDED = "CONSEIL_AJOUTE";
    private static final String KIND_REMOVED = "CONSEIL_RETIRE";
    private static final String KIND_THRESHOLD = "SEUIL_MODIFIE";
    private static final String KIND_PRIORITY = "PRIORITE_MODIFIEE";

    private static final String LIMITATION =
            "Ce rejeu compare ce qui a été conclu à ce que la base de connaissance actuelle "
            + "conclurait sur le MÊME relevé. Un écart constate que la connaissance a changé ; "
            + "il ne dit pas qu'elle a changé en mieux — c'est à vous d'en juger. "
            + "Le modèle d'image n'est pas rappelé (la photo n'est pas conservée) : sur un "
            + "diagnostic image, seuls les moteurs agronomiques sont rejoués. "
            + "Rien n'a été enregistré : cette simulation ne laisse aucune trace.";

    private final KnowledgeService knowledgeService;
    private final ConfidenceEvaluator confidenceEvaluator;

    /**
     * @param diagnostic      diagnostic d'origine, relu en base
     * @param original        recommandations telles qu'elles ont été émises
     * @param irrigationPlot  parcelle, pour que l'adaptation à l'irrigation
     *                        s'applique aussi au rejeu — sans quoi la comparaison
     *                        opposerait un conseil adapté à un conseil brut, et
     *                        l'écart mesurerait l'adaptation plutôt que la
     *                        connaissance
     */
    public DiagnosisReplay replay(Diagnostic diagnostic, List<Recommendation> original) {
        SensorReading reading = diagnostic.getReading();
        String cropName = diagnostic.getCropName();

        List<RecommendationItem> replayed = reading == null
                ? List.of()
                : recomputeItems(diagnostic, cropName, reading);

        DiagnosisReplay.Snapshot before = snapshotOf(
                diagnostic.getResult(), diagnostic.getConfidenceScore(), linesOfPersisted(original));

        DiagnosisReplay.Snapshot after = snapshotOf(
                diagnostic.getResult(), diagnostic.getConfidenceScore(), linesOfItems(replayed));

        List<DiagnosisReplay.Difference> differences = diff(before, after, reading == null);

        return DiagnosisReplay.builder()
                .diagnosticId(diagnostic.getId())
                .plotId(diagnostic.getPlot() == null ? null : diagnostic.getPlot().getId())
                .plotName(diagnostic.getPlot() == null ? null : diagnostic.getPlot().getName())
                .cropName(Culture.canonical(cropName))
                .source(diagnostic.getSource())
                .readingId(reading == null ? null : reading.getId())
                .readingRecordedAt(reading == null ? null : reading.getRecordedAt())
                .originalDiagnosedAt(diagnostic.getDiagnosedAt())
                .original(before)
                .replayed(after)
                .differences(differences)
                .identical(differences.isEmpty())
                .summary(summaryOf(differences, reading == null))
                .limitation(LIMITATION)
                .generatedAt(Instant.now())
                .build();
    }

    // ============================================================
    // Rejeu des moteurs
    // ============================================================

    /**
     * Rejoue les moteurs qui dépendent de la base de connaissance.
     *
     * <p>Le même enchaînement que {@code DiagnosisServiceImpl}, à trois exceptions
     * près, chacune motivée :
     *
     * <ul>
     *   <li><strong>pas de météo</strong> — elle interrogerait le fournisseur pour
     *       les prévisions d'<em>aujourd'hui</em>, sans rapport avec le relevé
     *       rejoué. L'écart mesurerait le ciel, pas la connaissance ;</li>
     *   <li><strong>pas de voisinage</strong> — même raison : l'état des parcelles
     *       voisines a changé depuis ;</li>
     *   <li><strong>pas d'alerte</strong> — un rejeu ne notifie personne.</li>
     * </ul>
     *
     * <p>Ne restent que les moteurs déterministes sur le relevé enregistré : ceux
     * dont le résultat est <em>exactement reproductible</em>, et donc les seuls sur
     * lesquels une comparaison a un sens.
     */
    private List<RecommendationItem> recomputeItems(Diagnostic diagnostic, String cropName,
                                                    SensorReading reading) {
        return recomputeItems(diagnostic.getPlot(), cropName, reading,
                diagnostic.getSource(), diagnostic.getResult());
    }

    /**
     * Rejoue les moteurs déterministes sur un relevé, <strong>sans exiger de
     * diagnostic</strong>.
     *
     * <p>Extraite de la variante privée ci-dessus pour servir aussi la vue
     * « diagnostic à l'instant T », où le point de départ est un {@code SensorReading}
     * et où <strong>il n'y a très souvent aucun diagnostic</strong> : entre le
     * régulateur de {@code DiagnosisThrottle} et les motifs {@code SONDE_DEFAILLANTE},
     * {@code CONTEXTE_ABSENT} et {@code ML_INDISPONIBLE}, un boîtier qui émet toutes
     * les trente secondes laisse la grande majorité de ses relevés sans conclusion.
     * C'est le cas nominal, pas l'exception.
     *
     * <p><strong>{@code result == null} : l'étape de classification est sautée.</strong>
     * On ne rappelle pas le microservice d'inférence pour la reconstituer, et ce n'est
     * pas une facilité :
     * <ul>
     *   <li>ce serait un appel réseau sur un chemin de <em>lecture</em> — la seule
     *       route en lecture seule du projet capable de rendre 503 parce qu'un service
     *       tiers est tombé ;</li>
     *   <li>le modèle a pu être réentraîné depuis, ce qui mêlerait deux variables à
     *       une comparaison dont l'objet est d'isoler l'effet de la <em>base de
     *       connaissance</em>. C'est exactement la raison pour laquelle le rejeu ne
     *       rappelle pas non plus le modèle d'image.</li>
     * </ul>
     * Restent les cinq moteurs agronomiques déterministes, et l'appelant le dit dans
     * sa {@code limitation}.
     *
     * @param plot     parcelle, nécessaire à {@code adaptToPlot} — un conseil
     *                 d'irrigation sur parcelle pluviale doit être reformulé des deux
     *                 côtés de la comparaison, sinon l'écart mesure l'adaptation et
     *                 non la connaissance
     * @param source   {@code IMAGE} ou {@code CAPTEUR} ; {@code null} saute l'étape
     * @param result   conclusion d'époque ; {@code null} saute l'étape
     */
    public List<RecommendationItem> recomputeItems(Plot plot, String cropName,
                                                   SensorReading reading,
                                                   String source, String result) {
        if (reading == null || cropName == null) {
            return List.of();
        }

        Long plotId = plot == null ? null : plot.getId();

        // Le stade n'est PAS recalculé pour aujourd'hui : ce serait comparer un
        // diagnostic pris en floraison à des seuils de maturation, et l'écart
        // mesurerait le temps écoulé plutôt que la connaissance. Faute de stade
        // historisé, on laisse null — CropRequirementResolver retombe alors sur les
        // seuils généraux de la culture, identiques des deux côtés de la comparaison.
        String growthStage = null;

        List<DiseaseRisk> risks = knowledgeService.assessRisks(cropName, reading);
        List<TrendFinding> trends = knowledgeService.assessTrends(cropName, growthStage, plotId);

        List<RecommendationItem> items = new ArrayList<>();

        // La voie « maladie » ou la voie « catégorie », selon la chaîne d'origine :
        // rejouer l'autre comparerait deux pipelines différents. Sans conclusion
        // d'époque, aucune des deux n'est jouable — voir la javadoc.
        if (result != null) {
            if ("IMAGE".equalsIgnoreCase(source)) {
                items.addAll(knowledgeService.recommendForDisease(result, cropName, reading));
            } else {
                items.addAll(knowledgeService.recommendForSensorDiagnostic(result, cropName));
            }
        }

        items.addAll(knowledgeService.analyzeAgronomic(cropName, growthStage, reading));
        items.addAll(knowledgeService.recommendForRisks(risks));
        items.addAll(knowledgeService.recommendForTrends(trends));

        items = new ArrayList<>(knowledgeService.arbitrate(cropName, items));
        items = new ArrayList<>(knowledgeService.adaptToPlot(plot, items));

        return items;
    }

    // ============================================================
    // Instantanés
    // ============================================================

    public DiagnosisReplay.Snapshot snapshotOf(String result, Double confidence,
                                                List<DiagnosisReplay.Snapshot.Line> lines) {
        return DiagnosisReplay.Snapshot.builder()
                .result(result)
                .confidenceScore(confidence)
                .confidenceLevel(confidenceEvaluator.level(confidence))
                .reliable(confidenceEvaluator.isReliable(confidence))
                .recommendationCount(lines.size())
                .recommendations(lines)
                .build();
    }

    public List<DiagnosisReplay.Snapshot.Line> linesOfPersisted(List<Recommendation> persisted) {
        if (persisted == null) {
            return List.of();
        }
        return persisted.stream()
                .map(r -> DiagnosisReplay.Snapshot.Line.builder()
                        .content(r.getContent())
                        .type(r.getRecommendationType())
                        .priority(r.getPriority())
                        // La catégorie n'est pas persistée sur Recommendation : elle
                        // est déduite du type, faute de mieux. La comparaison porte
                        // alors sur le champ de mesure, qui l'est.
                        .category(null)
                        .measureField(r.getMeasureField())
                        .observedValue(r.getObservedValue())
                        .thresholdValue(r.getThresholdValue())
                        .build())
                .toList();
    }

    public List<DiagnosisReplay.Snapshot.Line> linesOfItems(List<RecommendationItem> items) {
        return items.stream()
                .map(i -> DiagnosisReplay.Snapshot.Line.builder()
                        .content(i.getContent())
                        .type(i.getType())
                        .priority(i.getPriority())
                        .category(i.getCategory())
                        .measureField(i.getMeasureField())
                        .observedValue(i.getObservedValue())
                        .thresholdValue(i.getThresholdValue())
                        .build())
                .toList();
    }

    // ============================================================
    // Comparaison
    // ============================================================

    /**
     * Compare sur le couple {@code (type, champ de mesure)} plutôt que sur le texte.
     *
     * <p>Le texte d'un conseil change dès qu'une valeur mesurée y est interpolée :
     * comparer les libellés signalerait un écart à chaque rejeu, y compris quand
     * rien n'a bougé. Le couple type/mesure identifie <em>le conseil</em>
     * indépendamment de sa formulation, et c'est le seuil comparé ensuite qui dit ce
     * qui a réellement changé.
     */
    public List<DiagnosisReplay.Difference> diff(DiagnosisReplay.Snapshot before,
                                                  DiagnosisReplay.Snapshot after,
                                                  boolean readingMissing) {

        List<DiagnosisReplay.Difference> differences = new ArrayList<>();

        if (readingMissing) {
            // Sans relevé, aucun moteur agronomique n'a tourné à l'époque et aucun ne
            // tourne au rejeu : il n'y a rien à comparer. Le dire vaut mieux que de
            // rendre une liste vide, qui se lirait « rien n'a changé ».
            differences.add(DiagnosisReplay.Difference.builder()
                    .kind("REJEU_IMPOSSIBLE")
                    .kindLabel("Rejeu impossible")
                    .statement("Ce diagnostic a été produit sans relevé associé : aucun moteur "
                            + "agronomique n'a tourné, et il n'y a donc rien à rejouer.")
                    .build());
            return differences;
        }

        Map<String, DiagnosisReplay.Snapshot.Line> byKeyBefore = index(before.getRecommendations());
        Map<String, DiagnosisReplay.Snapshot.Line> byKeyAfter = index(after.getRecommendations());

        for (Map.Entry<String, DiagnosisReplay.Snapshot.Line> entry : byKeyAfter.entrySet()) {
            DiagnosisReplay.Snapshot.Line now = entry.getValue();
            DiagnosisReplay.Snapshot.Line then = byKeyBefore.get(entry.getKey());

            if (then == null) {
                differences.add(difference(KIND_ADDED, "Conseil ajouté", now.getCategory(),
                        String.format(FR,
                                "La connaissance actuelle produit un conseil %s que le diagnostic "
                                        + "d'origine ne portait pas : « %s »",
                                labelOfType(now.getType()), shorten(now.getContent()))));
                continue;
            }

            if (!Objects.equals(then.getThresholdValue(), now.getThresholdValue())) {
                differences.add(difference(KIND_THRESHOLD, "Seuil modifié", now.getCategory(),
                        String.format(FR,
                                "Le seuil appliqué à %s est passé de %s à %s.",
                                now.getMeasureField() == null ? "cette mesure" : now.getMeasureField(),
                                formatValue(then.getThresholdValue()),
                                formatValue(now.getThresholdValue()))));
            }

            if (!Objects.equals(then.getPriority(), now.getPriority())) {
                differences.add(difference(KIND_PRIORITY, "Priorité modifiée", now.getCategory(),
                        String.format(FR,
                                "La priorité de ce conseil est passée de %s à %s.",
                                then.getPriority(), now.getPriority())));
            }
        }

        for (Map.Entry<String, DiagnosisReplay.Snapshot.Line> entry : byKeyBefore.entrySet()) {
            if (byKeyAfter.containsKey(entry.getKey())) {
                continue;
            }
            DiagnosisReplay.Snapshot.Line then = entry.getValue();
            differences.add(difference(KIND_REMOVED, "Conseil retiré", then.getCategory(),
                    String.format(FR,
                            "La connaissance actuelle ne produirait plus ce conseil %s : « %s »",
                            labelOfType(then.getType()), shorten(then.getContent()))));
        }

        differences.sort(Comparator.comparing(DiagnosisReplay.Difference::getKind));
        return differences;
    }

    /**
     * Clé d'identité d'un conseil. {@code type} seul ne suffit pas — un diagnostic
     * porte plusieurs conseils agronomiques — et le champ de mesure les départage.
     */
    private Map<String, DiagnosisReplay.Snapshot.Line> index(
            List<DiagnosisReplay.Snapshot.Line> lines) {

        Map<String, DiagnosisReplay.Snapshot.Line> indexed = new LinkedHashMap<>();
        for (DiagnosisReplay.Snapshot.Line line : lines) {
            indexed.putIfAbsent(
                    (line.getType() == null ? "?" : line.getType())
                            + ":" + (line.getMeasureField() == null ? "-" : line.getMeasureField()),
                    line);
        }
        return indexed;
    }

    private DiagnosisReplay.Difference difference(String kind, String label,
                                                  String category, String statement) {
        return DiagnosisReplay.Difference.builder()
                .kind(kind)
                .kindLabel(label)
                .category(category)
                .statement(statement)
                .build();
    }

    private String summaryOf(List<DiagnosisReplay.Difference> differences, boolean readingMissing) {
        if (readingMissing) {
            return "Rejeu impossible : ce diagnostic n'a pas de relevé associé.";
        }
        if (differences.isEmpty()) {
            return "La base de connaissance actuelle produirait exactement les mêmes conseils "
                    + "sur ce relevé. Les ajustements faits depuis n'ont pas d'effet sur ce cas.";
        }
        return String.format(FR,
                "%d écart(s) entre les conseils émis et ceux que la connaissance actuelle "
                        + "produirait sur ce relevé. Un écart constate un changement, il ne "
                        + "l'approuve pas.",
                differences.size());
    }

    private String labelOfType(String type) {
        return type == null ? "" : "(" + type.toLowerCase(FR) + ")";
    }

    /** Le libellé complet peut faire plusieurs lignes : le diff n'en garde qu'un aperçu. */
    private String shorten(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 120 ? content : content.substring(0, 117) + "…";
    }

    private String formatValue(Double value) {
        return value == null ? "non défini" : String.format(FR, "%.2f", value);
    }
}
