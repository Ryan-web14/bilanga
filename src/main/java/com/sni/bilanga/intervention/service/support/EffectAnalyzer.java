package com.sni.bilanga.intervention.service.support;

import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.repository.DiagnosticRepository;
import com.sni.bilanga.enums.InterventionType;
import com.sni.bilanga.intervention.dto.response.InterventionEffect;
import com.sni.bilanga.intervention.model.Intervention;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.SensorReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Mesure ce qu'une intervention a changé, en comparant les fenêtres qui
 * l'encadrent.
 *
 * <p><strong>Pourquoi c'est la pièce qui compte.</strong> Jusqu'ici la chaîne
 * s'arrêtait au conseil : le système disait quoi faire, sans jamais savoir si
 * cela avait été fait, ni si cela avait marché. Fermer la boucle — conseil,
 * action, effet — est ce qui permet de <em>démontrer</em> l'apport de la
 * plateforme au lieu de le postuler.
 *
 * <p><strong>Et pourquoi le verdict reste prudent.</strong> Une comparaison
 * avant/après n'établit pas une causalité : une humidité qui remonte après une
 * irrigation a pu remonter grâce à une pluie tombée le même jour. Chaque
 * verdict porte donc sa réserve. Un chiffre livré sans réserve serait lu comme
 * une démonstration, et l'échantillon d'une exploitation ne l'autorise pas.
 */
@Component
@RequiredArgsConstructor
public class EffectAnalyzer {

    private static final Locale FR = Locale.FRANCE;

    /**
     * Fenêtre comparée de part et d'autre, en heures.
     *
     * <p>Quarante-huit heures : assez pour lisser le cycle jour/nuit — sans quoi
     * une irrigation faite le matin serait comparée à un après-midi et l'écart
     * mesurerait la météo plutôt que l'action — et assez court pour que l'effet
     * de l'intervention domine encore.
     */
    private static final int WINDOW_HOURS = 48;

    /** Volume borné : une fenêtre de deux jours ne justifie pas de tout rapatrier. */
    private static final int MAX_READINGS = 300;

    /**
     * En deçà, l'écart relatif est du bruit de mesure.
     *
     * Une sonde d'humidité varie de quelques pour cent sans que rien ne se soit
     * passé ; qualifier cela d'amélioration décrédibiliserait tous les autres
     * verdicts.
     */
    private static final double NEGLIGIBLE_CHANGE_RATIO = 0.05;

    private static final String VERDICT_IMPROVED = "AMELIORATION";
    private static final String VERDICT_UNCHANGED = "AUCUN_CHANGEMENT";
    private static final String VERDICT_WORSENED = "DEGRADATION";
    private static final String VERDICT_UNKNOWN = "INDETERMINE";

    private static final Map<String, Function<SensorReading, Double>> EXTRACTORS = Map.of(
            "humidite_sol", SensorReading::getHumiditeSol,
            "humidite_air", SensorReading::getHumiditeAir,
            "temperature", SensorReading::getTemperature,
            "ph", SensorReading::getPh,
            "azote", SensorReading::getAzote,
            "phosphore", SensorReading::getPhosphore,
            "potassium", SensorReading::getPotassium);

    private static final Map<String, String> LABELS = Map.of(
            "humidite_sol", "l'humidité du sol",
            "humidite_air", "l'humidité de l'air",
            "temperature", "la température",
            "ph", "le pH",
            "azote", "l'azote",
            "phosphore", "le phosphore",
            "potassium", "le potassium");

    private final SensorReadingRepository sensorReadingRepository;
    private final DiagnosticRepository diagnosticRepository;

    public InterventionEffect analyze(Intervention intervention) {
        InterventionType type = InterventionType.from(intervention.getType());
        Instant moment = intervention.getPerformedAt();
        Duration window = Duration.ofHours(WINDOW_HOURS);

        Instant beforeFrom = moment.minus(window);
        Instant afterTo = moment.plus(window);
        Long plotId = intervention.getPlot().getId();

        InterventionEffect.InterventionEffectBuilder effect = InterventionEffect.builder()
                .interventionId(intervention.getId())
                .type(intervention.getType())
                .typeLabel(type == null ? null : type.getLabel())
                .performedAt(moment)
                .windowHours(WINDOW_HOURS)
                .beforeFrom(beforeFrom).beforeTo(moment)
                .afterFrom(moment).afterTo(afterTo)
                .abnormalDiagnosesBefore(countAbnormal(plotId, beforeFrom, moment))
                .abnormalDiagnosesAfter(countAbnormal(plotId, moment, afterTo));

        // Un traitement phytosanitaire ne déplace aucune mesure de sonde : son
        // effet se lit sur les diagnostics qui suivent. Produire ici un écart
        // d'humidité donnerait un chiffre sans rapport, avec l'apparence de la
        // rigueur — ce qui est pire que de ne rien dire.
        if (type == null || !type.hasMeasurableEffect()) {
            return effect
                    .verdict(VERDICT_UNKNOWN)
                    .verdictLabel("Non mesurable par les sondes")
                    .statement(statementForNonMeasurable(type, effect.build()))
                    .limitation("Ce type d'intervention ne déplace aucune mesure de sonde. "
                                + "Son effet se juge sur l'évolution des diagnostics, et cela "
                                + "demande plusieurs cycles avant d'être concluant.")
                    .build();
        }

        String measure = type.getTargetMeasure();
        Function<SensorReading, Double> extractor = EXTRACTORS.get(measure);

        Double before = average(plotId, beforeFrom, moment, extractor);
        Double after = average(plotId, moment, afterTo, extractor);

        effect.targetMeasure(measure)
                .targetMeasureLabel(LABELS.getOrDefault(measure, measure))
                .beforeSampleCount(count(plotId, beforeFrom, moment, extractor))
                .afterSampleCount(count(plotId, moment, afterTo, extractor))
                .beforeAverage(round(before))
                .afterAverage(round(after));

        if (before == null || after == null) {
            return effect
                    .verdict(VERDICT_UNKNOWN)
                    .verdictLabel("Indéterminé")
                    .statement("Pas assez de relevés de part et d'autre de l'intervention "
                               + "pour en mesurer l'effet.")
                    .limitation("Une comparaison demande des mesures avant ET après. "
                                + "Vérifiez que le boîtier de la parcelle transmettait bien "
                                + "à cette période.")
                    .build();
        }

        double change = after - before;
        double ratio = Math.abs(before) < 0.001 ? 0d : change / Math.abs(before);

        String verdict = verdictFor(type, change, ratio);

        return effect
                .change(round(change))
                .changePercent(round(ratio * 100))
                .verdict(verdict)
                .verdictLabel(labelOf(verdict))
                .statement(statementFor(type, measure, before, after, change, ratio, verdict))
                .limitation("Cet écart constate une évolution, il n'établit pas une cause. "
                            + "Une pluie, un changement de température ou une autre intervention "
                            + "survenus dans la même fenêtre produiraient le même chiffre.")
                .build();
    }

    // ============================================================
    // Verdict
    // ============================================================

    /**
     * Le sens de l'amélioration dépend du type.
     *
     * Une irrigation doit faire <em>monter</em> l'humidité : le même écart
     * positif serait un succès ici et n'aurait aucun sens pour une intervention
     * censée faire baisser une mesure.
     */
    private String verdictFor(InterventionType type, double change, double ratio) {
        if (Math.abs(ratio) < NEGLIGIBLE_CHANGE_RATIO) {
            return VERDICT_UNCHANGED;
        }
        boolean wentUp = change > 0;
        return wentUp == type.isExpectsIncrease() ? VERDICT_IMPROVED : VERDICT_WORSENED;
    }

    private String labelOf(String verdict) {
        return switch (verdict) {
            case VERDICT_IMPROVED -> "Effet conforme à l'attendu";
            case VERDICT_WORSENED -> "Évolution contraire à l'attendu";
            case VERDICT_UNCHANGED -> "Aucun changement notable";
            default -> "Indéterminé";
        };
    }

    private String statementFor(InterventionType type, String measure,
                                double before, double after, double change,
                                double ratio, String verdict) {

        String label = LABELS.getOrDefault(measure, measure);
        String base = String.format(FR,
                "%s est passé%s de %.1f à %.1f dans les %d h qui ont suivi (%+.1f, soit %+.0f %%).",
                capitalize(label), label.endsWith("e") && !label.startsWith("le") ? "e" : "",
                before, after, WINDOW_HOURS, change, ratio * 100);

        return switch (verdict) {
            case VERDICT_IMPROVED -> base + " L'évolution va dans le sens attendu d'"
                    + type.getLabel().toLowerCase(FR) + ".";
            case VERDICT_WORSENED -> base + " L'évolution va à l'inverse de ce qu'"
                    + type.getLabel().toLowerCase(FR) + " devait produire : "
                    + "vérifiez le dosage, le matériel, ou cherchez une cause extérieure.";
            default -> base + " L'écart reste dans le bruit de mesure : rien ne permet "
                    + "de conclure à un effet.";
        };
    }

    private String statementForNonMeasurable(InterventionType type, InterventionEffect partial) {
        int before = partial.getAbnormalDiagnosesBefore() == null ? 0 : partial.getAbnormalDiagnosesBefore();
        int after = partial.getAbnormalDiagnosesAfter() == null ? 0 : partial.getAbnormalDiagnosesAfter();

        String action = type == null ? "Cette intervention" : type.getLabel();
        return String.format(FR,
                "%s ne se lit pas sur une mesure de sonde. Diagnostics anormaux : %d dans les "
                        + "%d h précédentes, %d dans les %d h suivantes. Un écart sur des effectifs "
                        + "aussi faibles ne conclut rien à lui seul.",
                action, before, WINDOW_HOURS, after, WINDOW_HOURS);
    }

    // ============================================================
    // Agrégats
    // ============================================================
    private List<SensorReading> readings(Long plotId, Instant from, Instant to) {
        return sensorReadingRepository
                .search(plotId, null, from, to, false, null, PageRequest.of(0, MAX_READINGS))
                .getContent();
    }

    private Double average(Long plotId, Instant from, Instant to,
                           Function<SensorReading, Double> extractor) {

        OptionalDouble average = readings(plotId, from, to).stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();

        return average.isPresent() ? average.getAsDouble() : null;
    }

    private Integer count(Long plotId, Instant from, Instant to,
                          Function<SensorReading, Double> extractor) {

        return (int) readings(plotId, from, to).stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .count();
    }

    /**
     * Diagnostics dont la conclusion n'est pas {@code NORMAL}.
     *
     * C'est le seul angle disponible pour juger un traitement : le fongicide ne
     * change aucune valeur de sonde, mais il doit faire disparaître la détection
     * de la maladie.
     */
    private Integer countAbnormal(Long plotId, Instant from, Instant to) {
        List<Diagnostic> diagnostics = diagnosticRepository
                .search(plotId, null, null, null, from, to, PageRequest.of(0, MAX_READINGS))
                .getContent();

        return (int) diagnostics.stream()
                .filter(d -> d.getResult() != null && !"NORMAL".equalsIgnoreCase(d.getResult()))
                .count();
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 100d) / 100d;
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
