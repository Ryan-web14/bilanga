package com.sni.bilanga.iot.service.support;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.enums.SensorHealth;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.iot.model.IotDevice;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.SensorReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Les trois règles, et surtout <strong>ce qui se passe sans témoin</strong>.
 *
 * <p>C'est la classe qui traite le seul angle mort capable de produire un conseil
 * <em>nuisible</em> : une sonde qui dérive en restant dans des valeurs crédibles.
 * La confiance du modèle mesure la certitude de la prédiction ; elle ne dit rien
 * de la fiabilité de la mesure qui l'a nourrie.
 *
 * <p>Un faux positif est coûteux dans l'autre sens : le verdict
 * {@code DEFAILLANTE} <em>inhibe le diagnostic</em>. Déclarer une panne à tort
 * prive la parcelle de tout conseil.
 */
@DisplayName("SensorHealthAnalyzer — juger la SONDE, non la mesure")
class SensorHealthAnalyzerTest {

    private SensorReadingRepository repository;
    private BilangaProperties.SensorHealth config;
    private SensorHealthAnalyzer analyzer;

    private static final long DEVICE_ID = 7L;
    private static final long PLOT_ID = 42L;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SensorReadingRepository.class);
        config = new BilangaProperties.SensorHealth();   // défauts : 6 / 12 h / 4 / 0,25 / 0,60
        analyzer = new SensorHealthAnalyzer(repository, config);

        noPeers();
    }

    // ============================================================
    // Les portes d'entrée
    // ============================================================

    @Nested
    @DisplayName("Avant toute analyse")
    class Preconditions {

        /**
         * L'interrupteur suit le principe des trois autres du projet : désactivé,
         * le système fonctionne, il fait juste moins. Aucune requête ne doit même
         * être émise.
         */
        @Test
        @DisplayName("désactivé, le verdict est SAINE et rien n'est interrogé")
        void disabledYieldsSoundWithoutQuerying() {
            config.setEnabled(false);

            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.SAINE);
            Mockito.verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("boîtier nul ou sans identifiant → SAINE, sans exception")
        void nullDeviceYieldsSound() {
            assertThat(analyzer.analyze(null).health()).isEqualTo(SensorHealth.SAINE);

            IotDevice unsaved = new IotDevice();
            assertThat(analyzer.analyze(unsaved).health()).isEqualTo(SensorHealth.SAINE);
        }

        /**
         * <strong>On ne déclare pas une panne sur une absence d'information.</strong>
         * Trois relevés ne permettent rien de conclure — et surtout pas d'inhiber
         * le diagnostic d'une parcelle fraîchement instrumentée.
         */
        @Test
        @DisplayName("série plus courte que min-points → SAINE")
        void tooFewReadingsYieldsSound() {
            ownReadings(frozenSeries(3, 41.2));   // figés, mais trop peu nombreux

            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.SAINE);
        }
    }

    // ============================================================
    // Règle 1 — valeur figée
    // ============================================================

    @Nested
    @DisplayName("Règle 1 — valeur figée")
    class Stuck {

        /**
         * L'égalité est <strong>exacte</strong>, et c'est voulu : une mesure
         * physique réelle varie toujours au moins sur sa dernière décimale. Deux
         * relevés strictement identiques arrivent ; six d'affilée ne sont plus un
         * phénomène naturel.
         */
        @Test
        @DisplayName("six relevés strictement identiques → DEFAILLANTE")
        void sixIdenticalReadingsAreFaulty() {
            ownReadings(frozenSeries(6, 41.2));

            SensorHealthAnalyzer.Verdict verdict = analyzer.analyze(device());

            assertThat(verdict.health()).isEqualTo(SensorHealth.DEFAILLANTE);
            assertThat(verdict.blocksDiagnosis())
                    .as("mieux vaut ne rien conseiller que conseiller faux")
                    .isTrue();
            assertThat(verdict.reason())
                    .contains("Valeur figée")
                    .contains("l'humidité du sol")
                    .contains("bloquée");
        }

        @Test
        @DisplayName("cinq relevés identiques ne suffisent pas — la borne est à six")
        void fiveIdenticalReadingsAreNotEnough() {
            List<SensorReading> readings = frozenSeries(5, 41.2);
            readings.add(reading(SensorReading::setHumiditeSol, 43.7));

            ownReadings(readings);

            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.SAINE);
        }

        @Test
        @DisplayName("le seuil suit la configuration")
        void thresholdFollowsConfiguration() {
            config.setStuckReadings(4);
            ownReadings(frozenSeries(4, 41.2));

            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.DEFAILLANTE);
        }

        /**
         * Une variation, même minuscule, disculpe la sonde : c'est précisément le
         * signe qu'elle mesure encore.
         */
        @Test
        @DisplayName("une variation sur la dernière décimale suffit à disculper")
        void tiniestVariationClearsTheSensor() {
            List<SensorReading> readings = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                readings.add(reading(SensorReading::setHumiditeSol, 41.2 + i * 0.01));
            }
            ownReadings(readings);

            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.SAINE);
        }

        /**
         * Une mesure absente rompt la série <em>sans rien prouver</em> : le boîtier
         * ne portait peut-être simplement pas cette sonde à ce moment-là.
         */
        @Test
        @DisplayName("une mesure absente rompt la série sans conclure à la panne")
        void missingValueBreaksTheStreakWithoutBlame() {
            List<SensorReading> readings = frozenSeries(6, 41.2);
            readings.set(3, new SensorReading());   // relevé sans humidité du sol

            ownReadings(readings);

            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.SAINE);
        }

        @Test
        @DisplayName("le motif nomme toutes les mesures figées")
        void reasonNamesEveryFrozenMeasure() {
            List<SensorReading> readings = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                SensorReading reading = new SensorReading();
                reading.setHumiditeSol(41.2);
                reading.setPh(6.4);
                readings.add(reading);
            }
            ownReadings(readings);

            assertThat(analyzer.analyze(device()).reason())
                    .contains("l'humidité du sol")
                    .contains("le pH");
        }
    }

    // ============================================================
    // L'absence de témoin — le cas le plus important
    // ============================================================

    @Nested
    @DisplayName("Sans voisin, aucune comparaison n'est possible")
    class NoPeer {

        /**
         * <strong>La limite assumée du système.</strong> En l'absence de témoin,
         * une dérive lente est rigoureusement indiscernable d'une évolution réelle
         * du sol. Se prononcer serait arbitraire — et un verdict arbitraire qui
         * inhibe le diagnostic est bien pire que pas de verdict du tout.
         */
        @Test
        @DisplayName("aucun voisin → SAINE, même sur une valeur très éloignée de la normale")
        void noPeerYieldsSound() {
            ownReadings(varyingSeries(10, 5.0));   // humidité du sol à 5 %, très basse
            noPeers();

            assertThat(analyzer.analyze(device()).health())
                    .as("sans témoin, rien ne distingue une sonde en dérive d'un sol sec")
                    .isEqualTo(SensorHealth.SAINE);
        }

        @Test
        @DisplayName("des voisins trop peu nombreux équivalent à aucun voisin")
        void tooFewPeerReadingsEqualNoPeer() {
            ownReadings(varyingSeries(10, 5.0));
            peerReadings(varyingSeries(2, 55.0));   // sous min-points

            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.SAINE);
        }

        /**
         * En revanche, la règle de la valeur figée reste applicable sans témoin —
         * et c'est déjà la plus fréquente en pratique.
         */
        @Test
        @DisplayName("mais la valeur figée reste détectée sans témoin")
        void stuckDetectionWorksWithoutPeer() {
            ownReadings(frozenSeries(6, 41.2));
            noPeers();

            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.DEFAILLANTE);
        }
    }

    // ============================================================
    // Règles 2 et 3 — décrochage et dérive
    // ============================================================

    @Nested
    @DisplayName("Règles 2 et 3 — confrontation aux voisins")
    class PeerComparison {

        /**
         * Écart rapporté à l'étendue observée chez les voisins. Ici les voisins
         * s'étalent de 50 à 60 (étendue 10, médiane 55) et le boîtier examiné est à
         * 20 : écart relatif de 3,5, très au-delà de la tolérance de décrochage
         * (0,60).
         */
        @Test
        @DisplayName("écart massif à la médiane des voisins → DEFAILLANTE")
        void massiveGapIsDecoupling() {
            ownReadings(varyingSeries(6, 20.0));
            peerReadings(spread(6, 50.0, 60.0));

            SensorHealthAnalyzer.Verdict verdict = analyzer.analyze(device());

            assertThat(verdict.health()).isEqualTo(SensorHealth.DEFAILLANTE);
            assertThat(verdict.reason())
                    .contains("Décrochage")
                    .contains("l'humidité du sol");
        }

        /**
         * Écart modéré : les voisins s'étalent de 50 à 60 (étendue 10, médiane 55),
         * le boîtier est à 51 — écart relatif de 0,40, entre les deux tolérances.
         * Les mesures <strong>restent utilisées</strong>, avec réserve.
         */
        @Test
        @DisplayName("écart modéré → SUSPECTE, et les mesures restent utilisées")
        void moderateGapIsDrift() {
            ownReadings(varyingSeries(6, 51.0));
            peerReadings(spread(6, 50.0, 60.0));

            SensorHealthAnalyzer.Verdict verdict = analyzer.analyze(device());

            assertThat(verdict.health()).isEqualTo(SensorHealth.SUSPECTE);
            assertThat(verdict.blocksDiagnosis())
                    .as("SUSPECTE porte une réserve, elle n'inhibe rien")
                    .isFalse();
            assertThat(verdict.warrantsCaution()).isTrue();
            assertThat(verdict.reason())
                    .contains("Écart persistant")
                    .contains("étalonner");
        }

        @Test
        @DisplayName("un boîtier aligné sur ses voisins est SAINE")
        void alignedDeviceIsSound() {
            ownReadings(varyingSeries(6, 55.0));
            peerReadings(spread(6, 50.0, 60.0));

            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.SAINE);
        }

        /**
         * <strong>Médiane et non moyenne.</strong> Avec trois boîtiers voisins dont
         * un déjà en panne à 5 %, la moyenne serait tirée vers le bas par le fautif
         * et disculperait celui qu'on examine. Ici les voisins valent 5, 55 et 56 :
         * médiane 55, moyenne 38,7. Le boîtier examiné à 20 doit être condamné par
         * la médiane, là où la moyenne l'aurait laissé passer.
         */
        @Test
        @DisplayName("la médiane résiste à un voisin déjà en panne, la moyenne non")
        void medianResistsAFaultyPeer() {
            ownReadings(varyingSeries(6, 20.0));

            List<SensorReading> peers = new ArrayList<>();
            peers.addAll(varyingSeries(2, 5.0));    // le voisin déjà en panne
            peers.addAll(varyingSeries(2, 55.0));
            peers.addAll(varyingSeries(2, 56.0));
            peerReadings(peers);

            assertThat(analyzer.analyze(device()).health())
                    .as("moyenne des voisins ≈ 38,7 — le boîtier à 20 aurait pu passer")
                    .isEqualTo(SensorHealth.DEFAILLANTE);
        }

        /**
         * <strong>La luminosité est délibérément exclue.</strong> Deux boîtiers
         * distants de quelques mètres, l'un à l'ombre et l'autre au soleil, relèvent
         * légitimement des valeurs très différentes. La comparer produirait un
         * décrochage permanent qui n'a rien d'une panne.
         */
        @Test
        @DisplayName("la luminosité n'entre pas dans la comparaison")
        void luminosityIsExcluded() {
            ownReadings(seriesOf(6, SensorReading::setLuminosite, 500.0));
            peerReadings(seriesOf(6, SensorReading::setLuminosite, 85_000.0));

            assertThat(analyzer.analyze(device()).health())
                    .as("l'ombre et le soleil ne sont pas une panne")
                    .isEqualTo(SensorHealth.SAINE);
        }

        @Test
        @DisplayName("une mesure que le boîtier ne porte pas est ignorée, non condamnée")
        void measureAbsentOnOneSideIsSkipped() {
            ownReadings(varyingSeries(6, 55.0));                    // humidité du sol seule
            peerReadings(seriesOf(6, SensorReading::setPh, 6.4));   // pH seul

            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.SAINE);
        }

        /**
         * Quand tous les voisins sont parfaitement d'accord — étendue nulle — on
         * retombe sur la valeur de référence comme unité, faute de mieux, et jamais
         * sur zéro. Ce test garde la porte fermée sur une division par zéro.
         */
        @Test
        @DisplayName("des voisins parfaitement identiques ne provoquent pas de division par zéro")
        void zeroRangeAmongPeersDoesNotDivideByZero() {
            ownReadings(varyingSeries(6, 20.0));
            peerReadings(seriesOf(6, SensorReading::setHumiditeSol, 55.0));

            SensorHealthAnalyzer.Verdict verdict = analyzer.analyze(device());

            assertThat(verdict.health()).isEqualTo(SensorHealth.DEFAILLANTE);
            assertThat(verdict.reason()).isNotNull();
        }

        @Test
        @DisplayName("les tolérances suivent la configuration")
        void tolerancesFollowConfiguration() {
            ownReadings(varyingSeries(6, 51.0));
            peerReadings(spread(6, 50.0, 60.0));

            // Écart relatif de 0,40 : suspect avec les défauts…
            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.SUSPECTE);

            // …et défaillant si l'on resserre la tolérance de décrochage.
            config.setDecouplingTolerance(0.30);
            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.DEFAILLANTE);

            // …et sain si l'on relâche celle de dérive.
            config.setDecouplingTolerance(0.60);
            config.setDriftTolerance(0.50);
            assertThat(analyzer.analyze(device()).health()).isEqualTo(SensorHealth.SAINE);
        }
    }

    // ============================================================
    // Priorité des règles
    // ============================================================

    @Nested
    @DisplayName("Priorité")
    class Precedence {

        /**
         * La valeur figée est constatée <em>avant</em> toute comparaison : c'est la
         * preuve la plus directe, et elle ne dépend d'aucun témoin. Le motif doit
         * donc parler de blocage, non de décrochage — le technicien saura remplacer
         * la sonde plutôt que chercher une hétérogénéité de terrain.
         */
        @Test
        @DisplayName("la valeur figée prime sur le décrochage, et le motif le dit")
        void stuckWinsOverDecoupling() {
            ownReadings(frozenSeries(6, 20.0));
            peerReadings(spread(6, 50.0, 60.0));

            SensorHealthAnalyzer.Verdict verdict = analyzer.analyze(device());

            assertThat(verdict.health()).isEqualTo(SensorHealth.DEFAILLANTE);
            assertThat(verdict.reason())
                    .contains("Valeur figée")
                    .doesNotContain("Décrochage");

            // Un voisin n'a même pas été interrogé : la conclusion tenait déjà.
            Mockito.verify(repository, Mockito.never())
                    .findPeerReadings(anyLong(), anyLong(), any(), any());
        }

        @Test
        @DisplayName("le décrochage prime sur la dérive")
        void decouplingWinsOverDrift() {
            // Le pH décroche largement, l'humidité dérive modérément.
            List<SensorReading> own = new ArrayList<>();
            List<SensorReading> peers = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                SensorReading mine = new SensorReading();
                mine.setPh(2.0 + i * 0.01);
                mine.setHumiditeSol(51.0 + i * 0.01);
                own.add(mine);

                SensorReading theirs = new SensorReading();
                theirs.setPh(6.0 + i * 0.1);
                theirs.setHumiditeSol(50.0 + i * 2.0);
                peers.add(theirs);
            }
            ownReadings(own);
            peerReadings(peers);

            SensorHealthAnalyzer.Verdict verdict = analyzer.analyze(device());

            assertThat(verdict.health()).isEqualTo(SensorHealth.DEFAILLANTE);
            assertThat(verdict.reason()).contains("Décrochage");
        }
    }

    // ============================================================
    // Le verdict SOUND
    // ============================================================

    @Nested
    @DisplayName("Verdict.SOUND")
    class SoundVerdict {

        @Test
        @DisplayName("ne porte ni motif, ni inhibition, ni réserve")
        void soundCarriesNothing() {
            assertThat(SensorHealthAnalyzer.Verdict.SOUND.health()).isEqualTo(SensorHealth.SAINE);
            assertThat(SensorHealthAnalyzer.Verdict.SOUND.reason()).isNull();
            assertThat(SensorHealthAnalyzer.Verdict.SOUND.blocksDiagnosis()).isFalse();
            assertThat(SensorHealthAnalyzer.Verdict.SOUND.warrantsCaution()).isFalse();
        }

        @Test
        @DisplayName("un verdict sans niveau ne bloque rien")
        void nullHealthBlocksNothing() {
            SensorHealthAnalyzer.Verdict verdict = new SensorHealthAnalyzer.Verdict(null, null);

            assertThat(verdict.blocksDiagnosis()).isFalse();
            assertThat(verdict.warrantsCaution()).isFalse();
        }
    }

    // ============================================================
    // Outillage
    // ============================================================

    private IotDevice device() {
        Plot plot = new Plot();
        plot.setId(PLOT_ID);

        IotDevice device = new IotDevice();
        device.setId(DEVICE_ID);
        device.setPlot(plot);
        return device;
    }

    private void ownReadings(List<SensorReading> readings) {
        Mockito.when(repository
                        .findByDevice_IdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
                                eq(DEVICE_ID), any(Instant.class), any(Pageable.class)))
                .thenReturn(readings);
    }

    private void peerReadings(List<SensorReading> readings) {
        Mockito.when(repository.findPeerReadings(
                        eq(PLOT_ID), eq(DEVICE_ID), any(Instant.class), any(Pageable.class)))
                .thenReturn(readings);
    }

    private void noPeers() {
        peerReadings(List.of());
    }

    /** N relevés portant la même valeur exacte d'humidité du sol. */
    private static List<SensorReading> frozenSeries(int size, double value) {
        return seriesOf(size, SensorReading::setHumiditeSol, value);
    }

    /** N relevés d'humidité du sol autour d'une valeur, variant légèrement. */
    private static List<SensorReading> varyingSeries(int size, double around) {
        List<SensorReading> readings = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            readings.add(reading(SensorReading::setHumiditeSol, around + i * 0.01));
        }
        return readings;
    }

    /** N relevés répartis linéairement entre deux bornes — étendue non nulle. */
    private static List<SensorReading> spread(int size, double min, double max) {
        List<SensorReading> readings = new ArrayList<>();
        double step = (max - min) / (size - 1);
        for (int i = 0; i < size; i++) {
            readings.add(reading(SensorReading::setHumiditeSol, min + i * step));
        }
        return readings;
    }

    private static List<SensorReading> seriesOf(int size,
                                                BiConsumer<SensorReading, Double> setter,
                                                double value) {
        List<SensorReading> readings = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            readings.add(reading(setter, value));
        }
        return readings;
    }

    private static SensorReading reading(BiConsumer<SensorReading, Double> setter, double value) {
        SensorReading reading = new SensorReading();
        setter.accept(reading, value);
        return reading;
    }
}
