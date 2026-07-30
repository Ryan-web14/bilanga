package com.sni.bilanga.diagnosis.service.support;

import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.repository.DiagnosticRepository;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.repository.SensorReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * Le choix du relevé et l'alignement du diagnostic — les deux seules décisions
 * non triviales de la vue « instant T ».
 *
 * <p>Trois propriétés ne se lisent pas dans une signature : que l'égalité parfaite
 * retienne le <em>passé</em> (donc que deux appels identiques rendent le même
 * relevé), que la tolérance dépassée rende {@code AUCUN} plutôt qu'un relevé
 * lointain, et que l'ordre des deux recherches de diagnostic ne s'inverse jamais.
 */
@DisplayName("PointInTimeResolver — quel relevé, et quelle conclusion d'alors")
class PointInTimeResolverTest {

    private SensorReadingRepository readingRepository;
    private DiagnosticRepository diagnosticRepository;
    private PointInTimeResolver resolver;

    private static final long PLOT_ID = 42L;
    private static final Instant AT = Instant.parse("2026-07-12T08:00:00Z");

    @BeforeEach
    void setUp() {
        readingRepository = Mockito.mock(SensorReadingRepository.class);
        diagnosticRepository = Mockito.mock(DiagnosticRepository.class);
        resolver = new PointInTimeResolver(readingRepository, diagnosticRepository);

        noReadingBefore();
        noReadingAfter();
        Mockito.when(diagnosticRepository.findFirstByReading_Id(anyLong()))
                .thenReturn(Optional.empty());
        Mockito.when(diagnosticRepository
                        .findFirstByPlot_IdAndDiagnosedAtLessThanEqualOrderByDiagnosedAtDesc(
                                anyLong(), any()))
                .thenReturn(Optional.empty());
    }

    // ============================================================
    // Choix du relevé
    // ============================================================

    @Nested
    @DisplayName("Choix du relevé")
    class ReadingSelection {

        @Test
        @DisplayName("relevé exactement à l'instant demandé → EXACT, écart nul")
        void exactMatch() {
            readingBefore(reading(1L, AT));

            var choice = resolver.resolveReading(PLOT_ID, AT, null);

            assertThat(choice.selection()).isEqualTo(PointInTimeResolver.SELECTION_EXACT);
            assertThat(choice.offsetMinutes()).isZero();
            assertThat(choice.reading().getId()).isEqualTo(1L);
        }

        /**
         * L'écart est <strong>signé</strong> : négatif quand le relevé précède
         * l'instant demandé. « 40 minutes plus tôt » et « 40 minutes plus tard » ne
         * se valent pas quand on cherche la cause d'un événement.
         */
        @Test
        @DisplayName("seul un relevé antérieur → AVANT, écart NÉGATIF")
        void onlyBeforeYieldsNegativeOffset() {
            readingBefore(reading(1L, AT.minus(40, ChronoUnit.MINUTES)));

            var choice = resolver.resolveReading(PLOT_ID, AT, null);

            assertThat(choice.selection()).isEqualTo(PointInTimeResolver.SELECTION_BEFORE);
            assertThat(choice.offsetMinutes()).isEqualTo(-40);
        }

        @Test
        @DisplayName("seul un relevé postérieur → APRES, écart POSITIF")
        void onlyAfterYieldsPositiveOffset() {
            readingAfter(reading(2L, AT.plus(15, ChronoUnit.MINUTES)));

            var choice = resolver.resolveReading(PLOT_ID, AT, null);

            assertThat(choice.selection()).isEqualTo(PointInTimeResolver.SELECTION_AFTER);
            assertThat(choice.offsetMinutes()).isEqualTo(15);
        }

        @Test
        @DisplayName("le plus proche des deux gagne")
        void closestWins() {
            readingBefore(reading(1L, AT.minus(50, ChronoUnit.MINUTES)));
            readingAfter(reading(2L, AT.plus(10, ChronoUnit.MINUTES)));

            assertThat(resolver.resolveReading(PLOT_ID, AT, null).reading().getId()).isEqualTo(2L);
        }

        /**
         * <strong>Le cas qui garantit le déterminisme.</strong> À écart identique on
         * retient le passé : on cherche l'état du sol <em>à</em> cet instant, et une
         * mesure postérieure décrit déjà autre chose. Surtout, deux appels identiques
         * doivent rendre le même relevé — une préférence implicite ne le garantirait
         * pas.
         */
        @Test
        @DisplayName("à écart ÉGAL, le passé gagne — et c'est déterministe")
        void tieGoesToThePast() {
            readingBefore(reading(1L, AT.minus(30, ChronoUnit.MINUTES)));
            readingAfter(reading(2L, AT.plus(30, ChronoUnit.MINUTES)));

            for (int i = 0; i < 5; i++) {
                var choice = resolver.resolveReading(PLOT_ID, AT, null);
                assertThat(choice.reading().getId()).isEqualTo(1L);
                assertThat(choice.selection()).isEqualTo(PointInTimeResolver.SELECTION_BEFORE);
            }
        }

        @Test
        @DisplayName("aucun relevé → AUCUN, sans exception")
        void noReadingYieldsNone() {
            var choice = resolver.resolveReading(PLOT_ID, AT, null);

            assertThat(choice.selection()).isEqualTo(PointInTimeResolver.SELECTION_NONE);
            assertThat(choice.isPresent()).isFalse();
            assertThat(choice.offsetMinutes()).isNull();
        }

        /**
         * Un relevé existe mais trop loin. Le rendre quand même laisserait croire à
         * une lecture de l'instant demandé, alors qu'il décrit un autre moment.
         */
        @Test
        @DisplayName("hors tolérance → AUCUN, même si un relevé existe")
        void beyondToleranceYieldsNone() {
            readingBefore(reading(1L, AT.minus(3, ChronoUnit.HOURS)));

            assertThat(resolver.resolveReading(PLOT_ID, AT, 60).selection())
                    .isEqualTo(PointInTimeResolver.SELECTION_NONE);
            assertThat(resolver.resolveReading(PLOT_ID, AT, 240).selection())
                    .as("la même donnée devient acceptable si l'on élargit")
                    .isEqualTo(PointInTimeResolver.SELECTION_BEFORE);
        }

        @Test
        @DisplayName("tolérance nulle ou négative → le défaut s'applique")
        void nonPositiveToleranceFallsBack() {
            readingBefore(reading(1L, AT.minus(2, ChronoUnit.HOURS)));

            for (Integer tolerance : new Integer[]{null, 0, -5}) {
                assertThat(resolver.resolveReading(PLOT_ID, AT, tolerance).isPresent())
                        .as("tolérance = %s", tolerance)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("parcelle ou instant absent → AUCUN, sans requête")
        void missingInputsQueryNothing() {
            assertThat(resolver.resolveReading(null, AT, null).isPresent()).isFalse();
            assertThat(resolver.resolveReading(PLOT_ID, null, null).isPresent()).isFalse();

            Mockito.verifyNoInteractions(readingRepository);
        }
    }

    // ============================================================
    // Alignement du diagnostic
    // ============================================================

    @Nested
    @DisplayName("Alignement du diagnostic")
    class DiagnosticAlignment {

        /**
         * <strong>L'ordre des deux recherches porte tout le sens.</strong> On cherche
         * d'abord la conclusion issue de ce relevé : c'est la réponse la plus forte.
         * L'inverser attribuerait à un relevé une conclusion qu'il n'a pas produite,
         * alors même qu'une conclusion issue de lui existait.
         */
        @Test
        @DisplayName("diagnostic issu de CE relevé → SUR_CE_RELEVE, âge nul")
        void diagnosticOnThisReading() {
            SensorReading reading = reading(1L, AT);
            Mockito.when(diagnosticRepository.findFirstByReading_Id(1L))
                    .thenReturn(Optional.of(diagnostic(9L, AT)));

            var choice = resolver.resolveDiagnostic(PLOT_ID, AT, reading);

            assertThat(choice.alignment()).isEqualTo(PointInTimeResolver.ALIGNMENT_ON_READING);
            assertThat(choice.ageMinutes()).isZero();

            Mockito.verify(diagnosticRepository, Mockito.never())
                    .findFirstByPlot_IdAndDiagnosedAtLessThanEqualOrderByDiagnosedAtDesc(
                            anyLong(), any());
        }

        /**
         * Le cas <strong>ordinaire</strong>, non l'exception : le régulateur et les
         * trois motifs d'abandon font que la plupart des relevés n'ont aucune
         * conclusion attachée.
         */
        @Test
        @DisplayName("aucun diagnostic sur le relevé → EN_VIGUEUR, avec son âge")
        void fallsBackToDiagnosticInForce() {
            SensorReading reading = reading(1L, AT);
            Mockito.when(diagnosticRepository
                            .findFirstByPlot_IdAndDiagnosedAtLessThanEqualOrderByDiagnosedAtDesc(
                                    PLOT_ID, AT))
                    .thenReturn(Optional.of(diagnostic(9L, AT.minus(25, ChronoUnit.MINUTES))));

            var choice = resolver.resolveDiagnostic(PLOT_ID, AT, reading);

            assertThat(choice.alignment()).isEqualTo(PointInTimeResolver.ALIGNMENT_IN_FORCE);
            assertThat(choice.ageMinutes()).isEqualTo(25);
        }

        @Test
        @DisplayName("aucun diagnostic du tout → AUCUN")
        void noDiagnosticAtAll() {
            var choice = resolver.resolveDiagnostic(PLOT_ID, AT, reading(1L, AT));

            assertThat(choice.alignment()).isEqualTo(PointInTimeResolver.ALIGNMENT_NONE);
            assertThat(choice.isPresent()).isFalse();
            assertThat(choice.ageMinutes()).isNull();
        }

        /**
         * Sans relevé, la recherche par identifiant est impossible — mais la
         * conclusion en vigueur reste une réponse légitime : elle ne dépend que de
         * l'instant.
         */
        @Test
        @DisplayName("sans relevé, la conclusion en vigueur reste trouvable")
        void worksWithoutReading() {
            Mockito.when(diagnosticRepository
                            .findFirstByPlot_IdAndDiagnosedAtLessThanEqualOrderByDiagnosedAtDesc(
                                    PLOT_ID, AT))
                    .thenReturn(Optional.of(diagnostic(9L, AT.minus(10, ChronoUnit.MINUTES))));

            var choice = resolver.resolveDiagnostic(PLOT_ID, AT, null);

            assertThat(choice.alignment()).isEqualTo(PointInTimeResolver.ALIGNMENT_IN_FORCE);
            Mockito.verify(diagnosticRepository, Mockito.never()).findFirstByReading_Id(anyLong());
        }

        @Test
        @DisplayName("un diagnostic sans date n'a pas d'âge, et ne lève pas")
        void diagnosticWithoutDateHasNoAge() {
            Mockito.when(diagnosticRepository
                            .findFirstByPlot_IdAndDiagnosedAtLessThanEqualOrderByDiagnosedAtDesc(
                                    PLOT_ID, AT))
                    .thenReturn(Optional.of(diagnostic(9L, null)));

            var choice = resolver.resolveDiagnostic(PLOT_ID, AT, null);

            assertThat(choice.alignment()).isEqualTo(PointInTimeResolver.ALIGNMENT_IN_FORCE);
            assertThat(choice.ageMinutes()).isNull();
        }

        @Test
        @DisplayName("parcelle ou instant absent → AUCUN, sans requête")
        void missingInputsQueryNothing() {
            assertThat(resolver.resolveDiagnostic(null, AT, null).isPresent()).isFalse();
            assertThat(resolver.resolveDiagnostic(PLOT_ID, null, null).isPresent()).isFalse();

            Mockito.verifyNoInteractions(diagnosticRepository);
        }
    }

    // ============================================================
    // Outillage
    // ============================================================

    private void readingBefore(SensorReading reading) {
        Mockito.when(readingRepository
                        .findFirstByPlot_IdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(PLOT_ID, AT))
                .thenReturn(Optional.ofNullable(reading));
    }

    private void readingAfter(SensorReading reading) {
        Mockito.when(readingRepository
                        .findFirstByPlot_IdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(PLOT_ID, AT))
                .thenReturn(Optional.ofNullable(reading));
    }

    private void noReadingBefore() {
        readingBefore(null);
    }

    private void noReadingAfter() {
        readingAfter(null);
    }

    private static SensorReading reading(Long id, Instant recordedAt) {
        SensorReading reading = new SensorReading();
        reading.setId(id);
        reading.setRecordedAt(recordedAt);
        return reading;
    }

    private static Diagnostic diagnostic(Long id, Instant diagnosedAt) {
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setId(id);
        diagnostic.setDiagnosedAt(diagnosedAt);
        diagnostic.setResult("STRESS_HYDRIQUE");
        return diagnostic;
    }
}
