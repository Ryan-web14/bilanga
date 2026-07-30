package com.sni.bilanga.iot.service.support;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.enums.SensorHealth;
import com.sni.bilanga.iot.model.IotDevice;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.SensorReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Juge la fiabilité des sondes d'un boîtier par cohérence, non par plausibilité.
 *
 * <p><strong>Ce que {@link PlausibilityChecker} ne peut pas voir.</strong> Il
 * n'attrape que l'absurde : un pH de 22, une humidité de 130 %. Or une sonde qui
 * tombe en panne renvoie rarement une valeur absurde. Le plus souvent elle se
 * fige sur sa dernière lecture, elle dérive lentement à mesure que l'électrode
 * s'encrasse, ou elle décroche de ses voisines — en restant tout du long dans
 * des valeurs parfaitement crédibles.
 *
 * <p><strong>Pourquoi c'est le point le plus important du système.</strong> Un
 * diagnostic fondé sur une sonde qui dérive est un diagnostic faux, présenté
 * avec exactement la même assurance qu'un diagnostic juste. La confiance du
 * modèle mesure la certitude de la prédiction ; elle ne dit rien de la fiabilité
 * de la mesure qui l'a nourrie. C'est le seul angle mort qui puisse produire un
 * conseil <em>nuisible</em> — irriguer un sol déjà saturé parce que la sonde
 * d'humidité est bloquée à 20 %.
 *
 * <p><strong>Trois règles, par ordre de gravité.</strong>
 * <ol>
 *   <li><em>Valeur figée</em> — la même valeur exacte sur N relevés consécutifs.
 *       Un sol vivant ne produit pas deux fois la même décimale ; c'est la
 *       signature d'une sonde bloquée. Verdict : défaillante.</li>
 *   <li><em>Décrochage</em> — écart massif à la médiane des boîtiers voisins de
 *       la même parcelle. Verdict : défaillante.</li>
 *   <li><em>Dérive</em> — écart modéré mais net à cette même médiane. Verdict :
 *       suspecte, sans inhiber le diagnostic.</li>
 * </ol>
 *
 * <p><strong>Sans voisin, pas de comparaison.</strong> Les deux dernières règles
 * exigent au moins un autre boîtier sur la parcelle. C'est une limite assumée :
 * en l'absence de témoin, une dérive lente est rigoureusement indiscernable
 * d'une évolution réelle du sol, et prétendre le contraire produirait des
 * verdicts arbitraires. Seule la règle de la valeur figée reste applicable, et
 * c'est déjà la plus fréquente en pratique.
 */
@Component
@RequiredArgsConstructor
public class SensorHealthAnalyzer {

    private static final Locale FR = Locale.FRANCE;

    /**
     * Mesures soumises au contrôle, avec leur libellé.
     *
     * <p>La luminosité en est délibérément absente : deux boîtiers distants de
     * quelques mètres, l'un à l'ombre et l'autre au soleil, relèvent
     * légitimement des valeurs très différentes. La comparer produirait un
     * décrochage permanent qui n'a rien d'une panne.
     */
    private static final Map<String, Function<SensorReading, Double>> MEASURES = new LinkedHashMap<>();

    private static final Map<String, String> LABELS = Map.of(
            "humidite_sol", "l'humidité du sol",
            "humidite_air", "l'humidité de l'air",
            "temperature", "la température de l'air",
            "temperature_sol", "la température du sol",
            "ph", "le pH",
            "azote", "l'azote",
            "phosphore", "le phosphore",
            "potassium", "le potassium");

    static {
        MEASURES.put("humidite_sol", SensorReading::getHumiditeSol);
        MEASURES.put("humidite_air", SensorReading::getHumiditeAir);
        MEASURES.put("temperature", SensorReading::getTemperature);
        MEASURES.put("temperature_sol", SensorReading::getTemperatureSol);
        MEASURES.put("ph", SensorReading::getPh);
        MEASURES.put("azote", SensorReading::getAzote);
        MEASURES.put("phosphore", SensorReading::getPhosphore);
        MEASURES.put("potassium", SensorReading::getPotassium);
    }

    private final SensorReadingRepository sensorReadingRepository;
    private final BilangaProperties.SensorHealth config;

    /**
     * Verdict porté sur un boîtier.
     *
     * @param health le verdict
     * @param reason motif en clair, destiné au technicien : c'est lui qui dit
     *               quelle sonde changer, là où un simple niveau le laisse deviner
     */
    public record Verdict(SensorHealth health, String reason) {

        public static final Verdict SOUND = new Verdict(SensorHealth.SAINE, null);

        public boolean blocksDiagnosis() {
            return health != null && health.blocksDiagnosis();
        }

        public boolean warrantsCaution() {
            return health != null && health.warrantsCaution();
        }
    }

    /**
     * Analyse la fenêtre récente d'un boîtier.
     *
     * <p>Ne lève jamais : l'appelant est le chemin d'ingestion, et un défaut
     * d'analyse ne doit en aucun cas coûter un relevé. Faute de données
     * suffisantes, le verdict est {@code SAINE} — on ne déclare pas une panne
     * sur une absence d'information.
     */
    public Verdict analyze(IotDevice device) {
        if (!config.isEnabled() || device == null || device.getId() == null) {
            return Verdict.SOUND;
        }

        Instant since = Instant.now().minus(Duration.ofHours(config.getWindowHours()));

        List<SensorReading> own = sensorReadingRepository
                .findByDevice_IdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
                        device.getId(), since, PageRequest.of(0, config.getMaxReadings()));

        if (own.size() < config.getMinPoints()) {
            return Verdict.SOUND;
        }

        Verdict stuck = detectStuck(own);
        if (stuck != null) {
            return stuck;
        }

        return compareToPeers(device, own, since);
    }

    // ============================================================
    // Règle 1 — valeur figée
    // ============================================================

    /**
     * Une sonde bloquée répète sa dernière lecture au bit près.
     *
     * <p>La comparaison est faite sur l'égalité exacte, et c'est voulu : une
     * mesure physique réelle varie toujours au moins sur sa dernière décimale.
     * Deux relevés strictement identiques peuvent arriver ; six d'affilée sur la
     * même mesure ne sont plus un phénomène naturel.
     */
    private Verdict detectStuck(List<SensorReading> readings) {
        List<String> frozen = new ArrayList<>();

        for (Map.Entry<String, Function<SensorReading, Double>> measure : MEASURES.entrySet()) {
            if (isFrozen(readings, measure.getValue())) {
                frozen.add(LABELS.getOrDefault(measure.getKey(), measure.getKey()));
            }
        }

        if (frozen.isEmpty()) {
            return null;
        }

        return new Verdict(SensorHealth.DEFAILLANTE, String.format(FR,
                "Valeur figée sur %d relevés consécutifs pour %s. La sonde est bloquée : "
                        + "elle répète sa dernière lecture au lieu de mesurer.",
                config.getStuckReadings(), String.join(", ", frozen)));
    }

    private boolean isFrozen(List<SensorReading> readings, Function<SensorReading, Double> extractor) {
        int needed = config.getStuckReadings();
        if (readings.size() < needed) {
            return false;
        }

        Double reference = null;
        int streak = 0;

        for (SensorReading reading : readings) {
            Double value = extractor.apply(reading);
            // Une mesure absente rompt la série sans rien prouver : le boîtier
            // ne portait peut-être simplement pas cette sonde à ce moment-là.
            if (value == null) {
                return false;
            }
            if (reference != null && reference.doubleValue() != value.doubleValue()) {
                return false;
            }
            reference = value;
            streak++;
            if (streak >= needed) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // Règles 2 et 3 — décrochage et dérive
    // ============================================================

    /**
     * Confronte la moyenne récente du boîtier à la médiane de ses voisins.
     *
     * <p>La médiane, et non la moyenne, comme référence des voisins : avec trois
     * boîtiers dont un déjà en panne, la moyenne serait tirée par le fautif et
     * disculperait celui qu'on examine. La médiane résiste à une valeur aberrante.
     *
     * <p>L'écart est rapporté à l'amplitude de la mesure et non à sa valeur
     * absolue : un écart de 2 sur un pH est considérable, le même écart sur une
     * concentration d'azote est négligeable.
     */
    private Verdict compareToPeers(IotDevice device, List<SensorReading> own, Instant since) {
        List<SensorReading> peers = sensorReadingRepository.findPeerReadings(
                device.getPlot().getId(), device.getId(), since,
                PageRequest.of(0, config.getMaxReadings()));

        if (peers.size() < config.getMinPoints()) {
            // Aucun témoin : une dérive lente est indiscernable d'une évolution
            // réelle du sol. Se prononcer ici serait arbitraire.
            return Verdict.SOUND;
        }

        List<String> decoupled = new ArrayList<>();
        List<String> drifting = new ArrayList<>();

        for (Map.Entry<String, Function<SensorReading, Double>> measure : MEASURES.entrySet()) {
            Double mine = average(own, measure.getValue());
            Double theirs = median(peers, measure.getValue());
            if (mine == null || theirs == null) {
                continue;
            }

            double amplitude = amplitude(peers, measure.getValue(), theirs);
            double relativeGap = Math.abs(mine - theirs) / amplitude;

            String label = LABELS.getOrDefault(measure.getKey(), measure.getKey());
            if (relativeGap > config.getDecouplingTolerance()) {
                decoupled.add(String.format(FR, "%s (%.1f contre %.1f)", label, mine, theirs));
            } else if (relativeGap > config.getDriftTolerance()) {
                drifting.add(String.format(FR, "%s (%.1f contre %.1f)", label, mine, theirs));
            }
        }

        if (!decoupled.isEmpty()) {
            return new Verdict(SensorHealth.DEFAILLANTE, String.format(FR,
                    "Décrochage par rapport aux autres boîtiers de la parcelle sur %s. "
                            + "L'écart est trop important pour refléter une hétérogénéité du terrain.",
                    String.join(", ", decoupled)));
        }

        if (!drifting.isEmpty()) {
            return new Verdict(SensorHealth.SUSPECTE, String.format(FR,
                    "Écart persistant aux autres boîtiers de la parcelle sur %s. "
                            + "Sonde vraisemblablement à étalonner ; les mesures restent utilisées, "
                            + "avec réserve.",
                    String.join(", ", drifting)));
        }

        return Verdict.SOUND;
    }

    // ============================================================
    // Statistiques
    // ============================================================

    private Double average(List<SensorReading> readings, Function<SensorReading, Double> extractor) {
        return readings.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream().boxed().findFirst().orElse(null);
    }

    private Double median(List<SensorReading> readings, Function<SensorReading, Double> extractor) {
        List<Double> values = readings.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();

        if (values.isEmpty()) {
            return null;
        }
        int middle = values.size() / 2;
        return values.size() % 2 == 1
                ? values.get(middle)
                : (values.get(middle - 1) + values.get(middle)) / 2d;
    }

    /**
     * Référence de normalisation de l'écart.
     *
     * <p>L'étendue observée chez les voisins dit combien la mesure varie
     * naturellement sur cette parcelle : c'est la bonne unité pour juger si un
     * écart est anormal. Quand les voisins sont tous d'accord — étendue
     * quasi nulle — on retombe sur la valeur de référence, faute de mieux, et
     * sur 1 en dernier recours pour ne jamais diviser par zéro.
     */
    private double amplitude(List<SensorReading> peers,
                             Function<SensorReading, Double> extractor,
                             double reference) {

        java.util.DoubleSummaryStatistics stats = peers.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();

        double range = stats.getMax() - stats.getMin();
        if (range > 0.001) {
            return range;
        }
        double magnitude = Math.abs(reference);
        return magnitude > 0.001 ? magnitude : 1d;
    }
}
