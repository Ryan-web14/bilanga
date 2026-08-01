package com.sni.bilanga.diagnosis.service.support;

import com.sni.bilanga.diagnosis.dto.response.DiagnosisReplay;
import com.sni.bilanga.diagnosis.dto.response.PointInTimeView;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.model.Recommendation;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Compose la vue « diagnostic à l'instant T » à partir des choix du résolveur.
 *
 * <p>Séparé de {@link PointInTimeResolver} : celui-ci décide <em>quoi</em> montrer,
 * celui-ci décide <em>comment le dire</em>. La frontière tient parce que la rédaction
 * des réserves est ici la partie la plus délicate — cette vue superpose trois choses
 * d'inégale solidité, et les présenter au même rang serait malhonnête.
 *
 * <p>Sans état ni transaction. Réutilise {@link DiagnosisReplayer} pour le recalcul
 * et les instantanés, plutôt que de dupliquer un second pipeline qui dériverait.
 */
@Component
@RequiredArgsConstructor
public class PointInTimeAssembler {

    private static final Locale FR = Locale.FRANCE;

    /** Mesures exposées, dans l'ordre du vocabulaire des moteurs. */
    private static final Map<String, Function<SensorReading, Double>> MEASURES =
            new LinkedHashMap<>();

    static {
        MEASURES.put("temperature", SensorReading::getTemperature);
        MEASURES.put("temperature_sol", SensorReading::getTemperatureSol);
        MEASURES.put("humidite_sol", SensorReading::getHumiditeSol);
        MEASURES.put("humidite_air", SensorReading::getHumiditeAir);
        MEASURES.put("ph", SensorReading::getPh);
        MEASURES.put("azote", SensorReading::getAzote);
        MEASURES.put("phosphore", SensorReading::getPhosphore);
        MEASURES.put("potassium", SensorReading::getPotassium);
        MEASURES.put("luminosite", SensorReading::getLuminosite);
        MEASURES.put("pluviometrie", SensorReading::getPluviometrie);
        MEASURES.put("conductivite_electrique", SensorReading::getConductiviteElectrique);
    }

    private final DiagnosisReplayer replayer;
    private final ConfidenceEvaluator confidenceEvaluator;

    /**
     * @param plot        parcelle, déjà chargée et contrôlée par l'appelant
     * @param cropName    culture retenue pour le recalcul ; {@code null} le désactive
     * @param at          instant demandé
     * @param readingPick choix du résolveur
     * @param diagPick    choix du résolveur
     * @param persisted   recommandations de la conclusion d'époque, si elle existe
     */
    public PointInTimeView assemble(Plot plot, String cropName, Instant at,
                                    PointInTimeResolver.ReadingChoice readingPick,
                                    PointInTimeResolver.DiagnosticChoice diagPick,
                                    List<Recommendation> persisted) {

        SensorReading reading = readingPick.reading();
        Diagnostic diagnostic = diagPick.diagnostic();

        DiagnosisReplay.Snapshot nowWould = recomputeSnapshot(plot, cropName, reading, diagnostic);

        DiagnosisReplay.Snapshot thenSnapshot = diagnostic == null
                ? null
                : replayer.snapshotOf(diagnostic.getCropName(), diagnostic.getResult(),
                        diagnostic.getConfidenceScore(),
                        replayer.linesOfPersisted(persisted));

        // Comparer n'a de sens que si les DEUX côtés existent. Sans conclusion
        // d'époque, une liste vide se lirait « rien n'a changé » — c'est faux, il n'y
        // avait rien à changer.
        List<DiagnosisReplay.Difference> differences =
                thenSnapshot == null || nowWould == null
                        ? List.of()
                        : replayer.diff(thenSnapshot, nowWould, false);

        return PointInTimeView.builder()
                .plotId(plot == null ? null : plot.getId())
                .plotName(plot == null ? null : plot.getName())
                .cropName(Culture.canonical(cropName))
                .requestedAt(at)
                .reading(readingRefOf(readingPick))
                .readingSelection(readingPick.selection())
                .diagnosedThen(diagnosticRefOf(diagnostic, persisted))
                .alignment(diagPick.alignment())
                .diagnosticAgeMinutes(diagPick.ageMinutes())
                .nowWouldConclude(nowWould)
                .differences(differences)
                .identical(differences.isEmpty())
                .summary(summaryOf(readingPick, diagPick, differences))
                .limitation(limitationOf(readingPick, diagPick, cropName))
                .generatedAt(Instant.now())
                .build();
    }

    // ============================================================
    // Recalcul
    // ============================================================

    /**
     * Ce que la connaissance actuelle conclurait sur ces mesures.
     *
     * <p>{@code result} et {@code source} viennent de la conclusion d'époque quand
     * elle existe — sans quoi l'étape de classification est sautée, et seuls les
     * moteurs agronomiques déterministes tournent. On ne rappelle jamais le
     * microservice d'inférence : voir la javadoc de
     * {@link DiagnosisReplayer#recomputeItems(Plot, String, SensorReading, String, String)}.
     *
     * <p><strong>La conclusion n'est jamais recalculée, seuls les conseils le sont.</strong>
     * Le {@code result} de l'instantané de droite recopie celui de gauche : sans
     * rappel du modèle, le système n'a aucun moyen de reconclure, et fabriquer une
     * conclusion serait le seul endroit de cette vue où l'on inventerait quelque chose.
     */
    private DiagnosisReplay.Snapshot recomputeSnapshot(Plot plot, String cropName,
                                                       SensorReading reading,
                                                       Diagnostic diagnostic) {
        if (reading == null || cropName == null) {
            return null;
        }

        String source = diagnostic == null ? null : diagnostic.getSource();
        String result = diagnostic == null ? null : diagnostic.getResult();

        List<RecommendationItem> items =
                replayer.recomputeItems(plot, cropName, reading, source, result);

        return replayer.snapshotOf(cropName, result,
                diagnostic == null ? null : diagnostic.getConfidenceScore(),
                replayer.linesOfItems(items));
    }

    // ============================================================
    // Références
    // ============================================================

    private PointInTimeView.ReadingRef readingRefOf(PointInTimeResolver.ReadingChoice pick) {
        if (!pick.isPresent()) {
            return null;
        }
        SensorReading reading = pick.reading();

        return PointInTimeView.ReadingRef.builder()
                .id(reading.getId())
                .recordedAt(reading.getRecordedAt())
                .offsetMinutes(pick.offsetMinutes())
                .measures(measuresOf(reading))
                .anomalyDetected(reading.getAnomalyDetected())
                .build();
    }

    /**
     * Mesures non nulles seulement.
     *
     * <p>Une mesure absente est <strong>omise</strong>, jamais rendue à zéro : un
     * boîtier ne porte pas forcément toutes les sondes, et « pH 0 » se lirait comme
     * une acidité extrême là où il n'y a pas de sonde de pH.
     */
    private Map<String, Double> measuresOf(SensorReading reading) {
        Map<String, Double> measures = new LinkedHashMap<>();
        MEASURES.forEach((name, extractor) -> {
            Double value = extractor.apply(reading);
            if (value != null) {
                measures.put(name, value);
            }
        });
        return measures;
    }

    private PointInTimeView.DiagnosticRef diagnosticRefOf(Diagnostic diagnostic,
                                                          List<Recommendation> persisted) {
        if (diagnostic == null) {
            return null;
        }
        List<DiagnosisReplay.Snapshot.Line> lines = replayer.linesOfPersisted(persisted);

        return PointInTimeView.DiagnosticRef.builder()
                .id(diagnostic.getId())
                .source(diagnostic.getSource())
                .result(diagnostic.getResult())
                .confidenceScore(diagnostic.getConfidenceScore())
                .confidenceLevel(confidenceEvaluator.level(diagnostic.getConfidenceScore()))
                .reliable(confidenceEvaluator.isReliable(diagnostic.getConfidenceScore()))
                .diagnosedAt(diagnostic.getDiagnosedAt())
                .readingId(diagnostic.getReading() == null ? null : diagnostic.getReading().getId())
                .recommendationCount(lines.size())
                .recommendations(lines)
                .build();
    }

    // ============================================================
    // Rédaction
    // ============================================================

    private String summaryOf(PointInTimeResolver.ReadingChoice readingPick,
                             PointInTimeResolver.DiagnosticChoice diagPick,
                             List<DiagnosisReplay.Difference> differences) {

        if (!readingPick.isPresent()) {
            return "Aucun relevé exploitable autour de cet instant : la parcelle ne "
                    + "transmettait pas, ou l'écart dépasse la tolérance demandée.";
        }

        StringBuilder text = new StringBuilder();
        text.append(switch (readingPick.selection()) {
            case PointInTimeResolver.SELECTION_EXACT -> "Relevé pris exactement à cet instant.";
            case PointInTimeResolver.SELECTION_BEFORE -> String.format(FR,
                    "Relevé le plus proche : %d minute(s) avant l'instant demandé.",
                    Math.abs(readingPick.offsetMinutes()));
            default -> String.format(FR,
                    "Relevé le plus proche : %d minute(s) après l'instant demandé.",
                    Math.abs(readingPick.offsetMinutes()));
        });

        text.append(' ').append(switch (diagPick.alignment()) {
            case PointInTimeResolver.ALIGNMENT_ON_READING ->
                    "Un diagnostic a été produit à partir de ce relevé même.";
            case PointInTimeResolver.ALIGNMENT_IN_FORCE -> String.format(FR,
                    "Ce relevé n'a produit aucun diagnostic ; celui qui s'affichait alors "
                            + "datait de %d minute(s) plus tôt.",
                    diagPick.ageMinutes() == null ? 0 : diagPick.ageMinutes());
            default -> "Aucun diagnostic n'était en vigueur à cet instant.";
        });

        if (diagPick.isPresent()) {
            text.append(differences.isEmpty()
                    ? " La base de connaissance actuelle produirait exactement les mêmes conseils."
                    : String.format(FR, " %d écart(s) avec ce que la connaissance actuelle "
                            + "produirait sur ces mesures.", differences.size()));
        }
        return text.toString();
    }

    /**
     * Les réserves, adaptées au cas rencontré.
     *
     * <p>Toujours renseignée : cette vue superpose des mesures enregistrées (exactes),
     * une conclusion peut-être seulement contemporaine, et un recalcul partiel. Les
     * présenter au même rang de solidité ferait passer une reconstitution pour un
     * enregistrement.
     */
    private String limitationOf(PointInTimeResolver.ReadingChoice readingPick,
                                PointInTimeResolver.DiagnosticChoice diagPick,
                                String cropName) {

        List<String> parts = new ArrayList<>();

        if (!readingPick.isPresent()) {
            parts.add("Aucun relevé n'a été retenu : rien n'a pu être recalculé. Élargissez "
                    + "la tolérance ou choisissez un instant où la parcelle transmettait.");
            return String.join(" ", parts);
        }

        if (!PointInTimeResolver.SELECTION_EXACT.equals(readingPick.selection())) {
            parts.add(String.format(FR,
                    "Les mesures affichées ont été relevées %d minute(s) %s l'instant demandé : "
                            + "elles en approchent l'état, elles ne le décrivent pas exactement.",
                    Math.abs(readingPick.offsetMinutes()),
                    readingPick.offsetMinutes() < 0 ? "avant" : "après"));
        }

        if (PointInTimeResolver.ALIGNMENT_IN_FORCE.equals(diagPick.alignment())) {
            parts.add("Le diagnostic montré n'a PAS été produit par ce relevé : c'est le dernier "
                    + "qui précédait, donc celui qui s'affichait alors. Le système ne conclut pas "
                    + "à chaque mesure — un intervalle minimal et l'absence de variation "
                    + "suffisent à ce qu'un relevé n'en produise aucun.");
        }

        if (!diagPick.isPresent()) {
            parts.add("Aucun diagnostic n'était en vigueur : c'est le cas ordinaire sur un "
                    + "relevé isolé, non une anomalie. Il n'y a donc rien à comparer, et les "
                    + "conseils affichés à droite sont ceux que la connaissance actuelle "
                    + "produirait — pas ceux qui avaient été donnés.");
        }

        if (cropName == null) {
            parts.add("Aucune culture n'a pu être rattachée à cette parcelle : les moteurs "
                    + "agronomiques n'ont pas tourné, faute de seuils de référence.");
        } else if (!diagPick.isPresent()) {
            parts.add("L'étape de classification par le modèle n'est pas rejouée — le modèle a "
                    + "pu être réentraîné depuis. Seuls les moteurs agronomiques déterministes "
                    + "ont été appliqués.");
        }

        parts.add("Rien n'a été enregistré : cette lecture ne laisse aucune trace.");
        return String.join(" ", parts);
    }
}
