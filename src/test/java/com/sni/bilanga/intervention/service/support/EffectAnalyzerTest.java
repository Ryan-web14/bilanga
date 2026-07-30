package com.sni.bilanga.intervention.service.support;

import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.repository.DiagnosticRepository;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.intervention.dto.response.InterventionEffect;
import com.sni.bilanga.intervention.model.Intervention;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.SensorReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

/**
 * Le sens de l'amélioration, le seuil de bruit, et le refus de chiffrer
 * l'inchiffrable.
 *
 * <p>C'est la classe qui ferme la boucle conseil → action → effet, donc celle qui
 * <em>évalue le système par lui-même</em>. Un verdict inversé ou un bruit de
 * mesure présenté comme un succès décrédibilise tous les autres.
 */
@DisplayName("EffectAnalyzer — mesurer ce qu'une intervention a changé")
class EffectAnalyzerTest {

    private SensorReadingRepository readingRepository;
    private DiagnosticRepository diagnosticRepository;
    private EffectAnalyzer analyzer;

    private static final Instant MOMENT = Instant.parse("2026-07-20T10:00:00Z");
    private static final long PLOT_ID = 42L;

    @BeforeEach
    void setUp() {
        readingRepository = Mockito.mock(SensorReadingRepository.class);
        diagnosticRepository = Mockito.mock(DiagnosticRepository.class);
        analyzer = new EffectAnalyzer(readingRepository, diagnosticRepository);

        noDiagnostics();
        noReadings();
    }

    // ============================================================
    // Le sens de l'amélioration dépend du type
    // ============================================================

    @Nested
    @DisplayName("Le sens de l'amélioration dépend du type d'intervention")
    class Direction {

        /**
         * Une irrigation doit faire <em>monter</em> l'humidité du sol. Le même
         * écart positif serait un succès ici et n'aurait aucun sens pour une
         * intervention censée faire baisser une mesure — d'où {@code expectsIncrease}
         * porté par l'énumération plutôt qu'une règle générale.
         */
        @Test
        @DisplayName("irrigation : l'humidité qui monte est une amélioration")
        void irrigationRaisingHumidityImproves() {
            readings(SensorReading::setHumiditeSol, 24.1, 43.8);

            InterventionEffect effect = analyzer.analyze(intervention("IRRIGATION"));

            assertThat(effect.getVerdict()).isEqualTo("AMELIORATION");
            assertThat(effect.getVerdictLabel()).isEqualTo("Effet conforme à l'attendu");
            assertThat(effect.getTargetMeasure()).isEqualTo("humidite_sol");
            assertThat(effect.getBeforeAverage()).isEqualTo(24.1);
            assertThat(effect.getAfterAverage()).isEqualTo(43.8);
            assertThat(effect.getChange()).isEqualTo(19.7);
            assertThat(effect.getChangePercent()).isEqualTo(81.74);
            assertThat(effect.getStatement())
                    .contains("24,1")
                    .contains("43,8")
                    .contains("sens attendu");
        }

        @Test
        @DisplayName("irrigation : l'humidité qui baisse est une dégradation")
        void irrigationDroppingHumidityWorsens() {
            readings(SensorReading::setHumiditeSol, 43.8, 24.1);

            InterventionEffect effect = analyzer.analyze(intervention("IRRIGATION"));

            assertThat(effect.getVerdict()).isEqualTo("DEGRADATION");
            assertThat(effect.getVerdictLabel()).isEqualTo("Évolution contraire à l'attendu");
            assertThat(effect.getStatement())
                    .contains("à l'inverse")
                    .as("le message doit orienter vers une cause, pas seulement constater")
                    .contains("dosage");
        }

        @Test
        @DisplayName("fertilisation : la mesure cible est l'azote, pas l'humidité")
        void fertilisationTargetsNitrogen() {
            readings(SensorReading::setAzote, 20.0, 45.0);

            InterventionEffect effect = analyzer.analyze(intervention("FERTILISATION"));

            assertThat(effect.getTargetMeasure()).isEqualTo("azote");
            assertThat(effect.getTargetMeasureLabel()).isEqualTo("l'azote");
            assertThat(effect.getVerdict()).isEqualTo("AMELIORATION");
        }
    }

    // ============================================================
    // Le seuil de bruit
    // ============================================================

    @Nested
    @DisplayName("Seuil de bruit — 5 % d'écart relatif")
    class NoiseFloor {

        /**
         * Une sonde d'humidité varie de quelques pour cent sans que rien ne se soit
         * passé. Qualifier cela d'amélioration décrédibiliserait tous les autres
         * verdicts — c'est le seuil qui protège la crédibilité de l'ensemble.
         */
        @Test
        @DisplayName("un écart de 4 % est du bruit, pas un effet")
        void fourPercentIsNoise() {
            readings(SensorReading::setHumiditeSol, 40.0, 41.6);   // +4 %

            InterventionEffect effect = analyzer.analyze(intervention("IRRIGATION"));

            assertThat(effect.getVerdict()).isEqualTo("AUCUN_CHANGEMENT");
            assertThat(effect.getStatement()).contains("bruit de mesure");
        }

        @Test
        @DisplayName("un écart de 6 % franchit le seuil")
        void sixPercentIsAnEffect() {
            readings(SensorReading::setHumiditeSol, 40.0, 42.4);   // +6 %

            assertThat(analyzer.analyze(intervention("IRRIGATION")).getVerdict())
                    .isEqualTo("AMELIORATION");
        }

        @Test
        @DisplayName("le seuil s'applique aussi à la baisse")
        void thresholdAppliesDownwardToo() {
            readings(SensorReading::setHumiditeSol, 40.0, 38.4);   // −4 %

            assertThat(analyzer.analyze(intervention("IRRIGATION")).getVerdict())
                    .isEqualTo("AUCUN_CHANGEMENT");
        }

        /**
         * Une valeur de départ quasi nulle rendrait le ratio infini. Le code borne
         * alors le ratio à zéro, ce qui classe l'écart en « aucun changement » —
         * prudent, plutôt que d'annoncer une amélioration de plusieurs milliers de
         * pour cent.
         */
        @Test
        @DisplayName("une valeur de départ nulle ne produit pas un ratio infini")
        void zeroBaselineDoesNotExplode() {
            readings(SensorReading::setHumiditeSol, 0.0, 30.0);

            InterventionEffect effect = analyzer.analyze(intervention("IRRIGATION"));

            assertThat(effect.getVerdict()).isEqualTo("AUCUN_CHANGEMENT");
            assertThat(effect.getChangePercent()).isEqualTo(0.0);
        }
    }

    // ============================================================
    // Ce qui n'est pas mesurable
    // ============================================================

    @Nested
    @DisplayName("Les types sans mesure cible")
    class NonMeasurable {

        /**
         * <strong>Le refus de chiffrer.</strong> Un traitement phytosanitaire ne
         * déplace aucune mesure de sonde. Produire ici un écart d'humidité donnerait
         * un chiffre sans rapport, <em>avec l'apparence de la rigueur</em> — ce qui
         * est pire que de ne rien dire.
         */
        @ParameterizedTest
        @ValueSource(strings = {"TRAITEMENT", "DESHERBAGE", "SEMIS", "RECOLTE", "AUTRE"})
        @DisplayName("verdict INDETERMINE, et aucune mesure cible n'est inventée")
        void nonMeasurableTypesYieldIndeterminate(String type) {
            readings(SensorReading::setHumiditeSol, 24.0, 44.0);   // l'humidité a bougé…

            InterventionEffect effect = analyzer.analyze(intervention(type));

            assertThat(effect.getVerdict()).isEqualTo("INDETERMINE");
            assertThat(effect.getVerdictLabel()).isEqualTo("Non mesurable par les sondes");
            assertThat(effect.getTargetMeasure())
                    .as("…mais elle n'a rien à voir avec un %s", type.toLowerCase())
                    .isNull();
            assertThat(effect.getChange()).isNull();
            assertThat(effect.getChangePercent()).isNull();
        }

        /**
         * Le seul angle disponible pour un traitement : le fongicide ne change
         * aucune valeur de sonde, mais il doit faire disparaître la détection de la
         * maladie.
         */
        @Test
        @DisplayName("l'analyse porte alors sur les diagnostics anormaux")
        void abnormalDiagnosesAreTheOnlyAngle() {
            diagnostics(3, 0);

            InterventionEffect effect = analyzer.analyze(intervention("TRAITEMENT"));

            assertThat(effect.getAbnormalDiagnosesBefore()).isEqualTo(3);
            assertThat(effect.getAbnormalDiagnosesAfter()).isZero();
            assertThat(effect.getStatement())
                    .contains("ne se lit pas sur une mesure de sonde")
                    .as("l'effectif est trop faible pour conclure, et cela doit être dit")
                    .contains("ne conclut rien");
        }

        @Test
        @DisplayName("un type hors vocabulaire est traité comme non mesurable")
        void unknownTypeIsNonMeasurable() {
            InterventionEffect effect = analyzer.analyze(intervention("N_IMPORTE_QUOI"));

            assertThat(effect.getVerdict()).isEqualTo("INDETERMINE");
            assertThat(effect.getTypeLabel()).isNull();
        }
    }

    // ============================================================
    // Données insuffisantes
    // ============================================================

    @Nested
    @DisplayName("Sans mesures des deux côtés")
    class InsufficientData {

        /**
         * Une comparaison demande des mesures avant <strong>et</strong> après.
         * L'absence d'un côté doit produire un verdict indéterminé assorti d'une
         * piste de résolution — non un zéro qui se lirait comme un effondrement.
         */
        @Test
        @DisplayName("aucun relevé du tout → INDETERMINE, avec la piste à vérifier")
        void noReadingsYieldIndeterminate() {
            noReadings();

            InterventionEffect effect = analyzer.analyze(intervention("IRRIGATION"));

            assertThat(effect.getVerdict()).isEqualTo("INDETERMINE");
            assertThat(effect.getBeforeAverage()).isNull();
            assertThat(effect.getAfterAverage()).isNull();
            assertThat(effect.getStatement()).contains("Pas assez de relevés");
            assertThat(effect.getLimitation()).contains("avant ET après");
        }

        @Test
        @DisplayName("des relevés d'un seul côté ne suffisent pas")
        void oneSidedReadingsAreNotEnough() {
            Mockito.when(readingRepository.search(eq(PLOT_ID), isNull(),
                            eq(MOMENT.minusSeconds(48 * 3600)), eq(MOMENT),
                            eq(false), isNull(), any(Pageable.class)))
                    .thenReturn(page(List.of(reading(SensorReading::setHumiditeSol, 24.0))));

            InterventionEffect effect = analyzer.analyze(intervention("IRRIGATION"));

            assertThat(effect.getVerdict()).isEqualTo("INDETERMINE");
            assertThat(effect.getBeforeAverage()).isEqualTo(24.0);
            assertThat(effect.getAfterAverage()).isNull();
        }

        @Test
        @DisplayName("les relevés sans la mesure cible ne comptent pas")
        void readingsWithoutTargetMeasureDoNotCount() {
            // Des relevés existent, mais aucun ne porte l'humidité du sol.
            readings(SensorReading::setPh, 6.4, 6.5);

            InterventionEffect effect = analyzer.analyze(intervention("IRRIGATION"));

            assertThat(effect.getVerdict()).isEqualTo("INDETERMINE");
            assertThat(effect.getBeforeSampleCount()).isZero();
        }
    }

    // ============================================================
    // La fenêtre et la réserve
    // ============================================================

    @Nested
    @DisplayName("La fenêtre de 48 h et la réserve obligatoire")
    class WindowAndCaveat {

        /**
         * Quarante-huit heures : assez pour lisser le cycle jour/nuit — sans quoi
         * une irrigation faite le matin serait comparée à un après-midi et l'écart
         * mesurerait la météo plutôt que l'action — et assez court pour que l'effet
         * de l'intervention domine encore.
         */
        @Test
        @DisplayName("les bornes encadrent l'intervention de 48 h de part et d'autre")
        void windowIsSymmetric() {
            readings(SensorReading::setHumiditeSol, 24.0, 44.0);

            InterventionEffect effect = analyzer.analyze(intervention("IRRIGATION"));

            assertThat(effect.getWindowHours()).isEqualTo(48);
            assertThat(effect.getBeforeFrom()).isEqualTo(MOMENT.minusSeconds(48 * 3600));
            assertThat(effect.getBeforeTo()).isEqualTo(MOMENT);
            assertThat(effect.getAfterFrom()).isEqualTo(MOMENT);
            assertThat(effect.getAfterTo()).isEqualTo(MOMENT.plusSeconds(48 * 3600));
        }

        /**
         * <strong>Toujours renseignée, quel que soit le verdict.</strong> Une
         * comparaison avant/après n'établit jamais une causalité : une pluie tombée
         * dans la même fenêtre produirait exactement le même chiffre. Un chiffre
         * livré sans cette réserve serait lu comme une démonstration.
         */
        @ParameterizedTest
        @ValueSource(strings = {"IRRIGATION", "FERTILISATION", "TRAITEMENT", "SEMIS"})
        @DisplayName("la réserve est renseignée dans tous les cas")
        void limitationIsAlwaysPresent(String type) {
            readings(SensorReading::setHumiditeSol, 24.0, 44.0);

            assertThat(analyzer.analyze(intervention(type)).getLimitation())
                    .as("%s : un chiffre sans réserve est lu comme une démonstration", type)
                    .isNotNull()
                    .isNotBlank();
        }

        @Test
        @DisplayName("pour un type mesurable, elle nomme les causes concurrentes")
        void limitationNamesCompetingCauses() {
            readings(SensorReading::setHumiditeSol, 24.0, 44.0);

            assertThat(analyzer.analyze(intervention("IRRIGATION")).getLimitation())
                    .contains("n'établit pas une cause")
                    .contains("pluie");
        }

        @Test
        @DisplayName("les moyennes sont arrondies à deux décimales")
        void averagesAreRounded() {
            readings(SensorReading::setHumiditeSol, 24.123456, 43.987654);

            InterventionEffect effect = analyzer.analyze(intervention("IRRIGATION"));

            assertThat(effect.getBeforeAverage()).isEqualTo(24.12);
            assertThat(effect.getAfterAverage()).isEqualTo(43.99);
        }
    }

    // ============================================================
    // Outillage
    // ============================================================

    private Intervention intervention(String type) {
        Plot plot = new Plot();
        plot.setId(PLOT_ID);

        Intervention intervention = new Intervention();
        intervention.setId(1L);
        intervention.setType(type);
        intervention.setPerformedAt(MOMENT);
        intervention.setPlot(plot);
        return intervention;
    }

    /** Une valeur avant et une valeur après, sur la même mesure. */
    private void readings(BiConsumer<SensorReading, Double> setter, double before, double after) {
        Instant windowStart = MOMENT.minusSeconds(48 * 3600);
        Instant windowEnd = MOMENT.plusSeconds(48 * 3600);

        Mockito.when(readingRepository.search(eq(PLOT_ID), isNull(), eq(windowStart), eq(MOMENT),
                        eq(false), isNull(), any(Pageable.class)))
                .thenReturn(page(List.of(reading(setter, before))));

        Mockito.when(readingRepository.search(eq(PLOT_ID), isNull(), eq(MOMENT), eq(windowEnd),
                        eq(false), isNull(), any(Pageable.class)))
                .thenReturn(page(List.of(reading(setter, after))));
    }

    private void noReadings() {
        Mockito.when(readingRepository.search(anyLong(), any(), any(), any(),
                        any(Boolean.class), any(), any(Pageable.class)))
                .thenReturn(page(List.of()));
    }

    private void diagnostics(int abnormalBefore, int abnormalAfter) {
        Mockito.when(diagnosticRepository.search(eq(PLOT_ID), any(), any(), any(),
                        eq(MOMENT.minusSeconds(48 * 3600)), eq(MOMENT), any(Pageable.class)))
                .thenReturn(page(abnormal(abnormalBefore)));

        Mockito.when(diagnosticRepository.search(eq(PLOT_ID), any(), any(), any(),
                        eq(MOMENT), eq(MOMENT.plusSeconds(48 * 3600)), any(Pageable.class)))
                .thenReturn(page(abnormal(abnormalAfter)));
    }

    private void noDiagnostics() {
        Mockito.when(diagnosticRepository.search(anyLong(), any(), any(), any(),
                        any(), any(), any(Pageable.class)))
                .thenReturn(page(List.of()));
    }

    private static List<Diagnostic> abnormal(int count) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Diagnostic diagnostic = new Diagnostic();
            diagnostic.setResult("Late_blight");
            diagnostics.add(diagnostic);
        }
        return diagnostics;
    }

    private static SensorReading reading(BiConsumer<SensorReading, Double> setter, double value) {
        SensorReading reading = new SensorReading();
        setter.accept(reading, value);
        return reading;
    }

    private static <T> Page<T> page(List<T> content) {
        return new PageImpl<>(content);
    }
}
