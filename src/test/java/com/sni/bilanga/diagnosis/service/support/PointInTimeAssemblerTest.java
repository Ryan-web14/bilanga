package com.sni.bilanga.diagnosis.service.support;

import com.sni.bilanga.diagnosis.dto.response.PointInTimeView;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.model.Recommendation;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.knowledge.service.interfaces.KnowledgeService;
import com.sni.bilanga.knowledge.service.support.DiseaseLabeller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Ce que la vue dit, et surtout ce qu'elle refuse de laisser croire.
 *
 * <p>Trois propriétés à figer : une comparaison n'est produite que si les deux côtés
 * existent (une liste vide se lirait « rien n'a changé », ce qui est faux quand il
 * n'y avait rien) ; la réserve mentionne l'absence de diagnostic <em>exactement</em>
 * quand il n'y en a pas ; et une mesure absente est omise, jamais rendue à zéro.
 */
@DisplayName("PointInTimeAssembler — trois solidités inégales, dites comme telles")
class PointInTimeAssemblerTest {

    private KnowledgeService knowledgeService;
    private PointInTimeAssembler assembler;

    private static final Instant AT = Instant.parse("2026-07-12T08:00:00Z");

    @BeforeEach
    void setUp() {
        knowledgeService = Mockito.mock(KnowledgeService.class);
        ConfidenceEvaluator confidence = new ConfidenceEvaluator(knowledgeService,
                Mockito.mock(DiseaseLabeller.class), properties());
        DiagnosisReplayer replayer = new DiagnosisReplayer(knowledgeService, confidence);
        assembler = new PointInTimeAssembler(replayer, confidence);

        // Les moteurs ne rendent rien par défaut : ce test porte sur l'assemblage,
        // pas sur la connaissance.
        Mockito.when(knowledgeService.assessRisks(anyString(), any())).thenReturn(List.of());
        Mockito.when(knowledgeService.assessTrends(anyString(), any(), any())).thenReturn(List.of());
        Mockito.when(knowledgeService.recommendForRisks(any())).thenReturn(List.of());
        Mockito.when(knowledgeService.recommendForTrends(any())).thenReturn(List.of());
        Mockito.when(knowledgeService.analyzeAgronomic(anyString(), any(), any())).thenReturn(List.of());
        Mockito.when(knowledgeService.recommendForDisease(any(), anyString(), any())).thenReturn(List.of());
        Mockito.when(knowledgeService.recommendForSensorDiagnostic(any(), anyString())).thenReturn(List.of());
        Mockito.when(knowledgeService.arbitrate(anyString(), any()))
                .thenAnswer(call -> call.getArgument(1));
        Mockito.when(knowledgeService.adaptToPlot(any(), any()))
                .thenAnswer(call -> call.getArgument(1));
    }

    private static com.sni.bilanga.config.properties.BilangaProperties.Confidence properties() {
        return new com.sni.bilanga.config.properties.BilangaProperties.Confidence();
    }

    // ============================================================
    // Alignement
    // ============================================================

    @Nested
    @DisplayName("L'alignement est reporté tel quel, jamais deviné")
    class Alignment {

        @Test
        @DisplayName("SUR_CE_RELEVE quand les identifiants de relevé coïncident")
        void onReadingIsReported() {
            SensorReading reading = reading(1L, AT, 24.0);
            Diagnostic diagnostic = diagnostic(9L, AT, reading);

            PointInTimeView view = assembler.assemble(plot(), "tomate", AT,
                    readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                    diagPick(diagnostic, PointInTimeResolver.ALIGNMENT_ON_READING, 0),
                    List.of());

            assertThat(view.getAlignment()).isEqualTo(PointInTimeResolver.ALIGNMENT_ON_READING);
            assertThat(view.getDiagnosedThen().getReadingId()).isEqualTo(1L);
            assertThat(view.getLimitation())
                    .as("aucune réserve d'alignement quand la conclusion vient bien du relevé")
                    .doesNotContain("n'a PAS été produit par ce relevé");
        }

        /**
         * Le cas <strong>ordinaire</strong>. La réserve doit l'énoncer, sinon
         * l'utilisateur attribue à ses mesures une conclusion qu'elles n'ont pas
         * produite.
         */
        @Test
        @DisplayName("EN_VIGUEUR : la réserve dit que la conclusion ne vient pas de ce relevé")
        void inForceIsFlaggedInLimitation() {
            SensorReading reading = reading(1L, AT, 24.0);
            Diagnostic diagnostic = diagnostic(9L, AT.minus(25, ChronoUnit.MINUTES), null);

            PointInTimeView view = assembler.assemble(plot(), "tomate", AT,
                    readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                    diagPick(diagnostic, PointInTimeResolver.ALIGNMENT_IN_FORCE, 25),
                    List.of());

            assertThat(view.getDiagnosticAgeMinutes()).isEqualTo(25);
            assertThat(view.getLimitation())
                    .contains("n'a PAS été produit par ce relevé")
                    .contains("ne conclut pas à chaque mesure");
            assertThat(view.getSummary()).contains("25 minute(s) plus tôt");
        }
    }

    // ============================================================
    // Comparer n'a de sens qu'à deux
    // ============================================================

    @Nested
    @DisplayName("La comparaison n'est produite que si les deux côtés existent")
    class Comparison {

        /**
         * <strong>Le piège de conception.</strong> Sans conclusion d'époque, rendre une
         * liste d'écarts vide se lirait « rien n'a changé » — alors qu'il n'y avait
         * rien à changer. La réserve doit le dire explicitement.
         */
        @Test
        @DisplayName("aucun diagnostic d'époque → aucun écart, ET la réserve l'explique")
        void noDiagnosticMeansNothingToCompare() {
            SensorReading reading = reading(1L, AT, 24.0);

            PointInTimeView view = assembler.assemble(plot(), "tomate", AT,
                    readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                    PointInTimeResolver.DiagnosticChoice.NONE,
                    List.of());

            assertThat(view.getDiagnosedThen()).isNull();
            assertThat(view.getDifferences()).isEmpty();
            assertThat(view.getLimitation())
                    .contains("Aucun diagnostic n'était en vigueur")
                    .contains("c'est le cas ordinaire")
                    .contains("rien à comparer");
            assertThat(view.getNowWouldConclude())
                    .as("le recalcul a tout de même lieu : c'est la moitié droite de la vue")
                    .isNotNull();
        }

        @Test
        @DisplayName("aucun relevé → rien n'est recalculé, et la réserve le dit")
        void noReadingMeansNoRecomputation() {
            PointInTimeView view = assembler.assemble(plot(), "tomate", AT,
                    PointInTimeResolver.ReadingChoice.NONE,
                    PointInTimeResolver.DiagnosticChoice.NONE,
                    List.of());

            assertThat(view.getReading()).isNull();
            assertThat(view.getNowWouldConclude()).isNull();
            assertThat(view.getDifferences()).isEmpty();
            assertThat(view.getLimitation())
                    .contains("Aucun relevé n'a été retenu")
                    .contains("Élargissez la tolérance");
            assertThat(view.getSummary()).contains("Aucun relevé exploitable");
        }

        @Test
        @DisplayName("les deux côtés présents et identiques → identical = true")
        void identicalWhenNothingChanged() {
            SensorReading reading = reading(1L, AT, 24.0);
            Diagnostic diagnostic = diagnostic(9L, AT, reading);

            PointInTimeView view = assembler.assemble(plot(), "tomate", AT,
                    readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                    diagPick(diagnostic, PointInTimeResolver.ALIGNMENT_ON_READING, 0),
                    List.of());

            assertThat(view.getIdentical()).isTrue();
            assertThat(view.getSummary()).contains("exactement les mêmes conseils");
        }

        @Test
        @DisplayName("un conseil apparu depuis produit un écart CONSEIL_AJOUTE")
        void addedAdviceProducesADifference() {
            SensorReading reading = reading(1L, AT, 24.0);
            Diagnostic diagnostic = diagnostic(9L, AT, reading);

            Mockito.when(knowledgeService.analyzeAgronomic(anyString(), any(), any()))
                    .thenReturn(List.of(RecommendationItem.builder()
                            .content("Irriguez : humidité sous le seuil.")
                            .type("AGRONOMIQUE").priority("HAUTE")
                            .category("STRESS_HYDRIQUE")
                            .measureField("humidite_sol")
                            .observedValue(24.0).thresholdValue(35.0)
                            .build()));

            PointInTimeView view = assembler.assemble(plot(), "tomate", AT,
                    readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                    diagPick(diagnostic, PointInTimeResolver.ALIGNMENT_ON_READING, 0),
                    List.of());

            assertThat(view.getIdentical()).isFalse();
            assertThat(view.getDifferences())
                    .extracting(d -> d.getKind())
                    .contains("CONSEIL_AJOUTE");
        }
    }

    // ============================================================
    // Le relevé exposé
    // ============================================================

    @Nested
    @DisplayName("Le relevé exposé")
    class ReadingExposure {

        /**
         * Une mesure absente est <strong>omise</strong>. Un boîtier ne porte pas
         * forcément toutes les sondes, et « pH 0 » se lirait comme une acidité extrême
         * là où il n'y a simplement pas de sonde de pH.
         */
        @Test
        @DisplayName("les mesures absentes sont omises, pas rendues à zéro")
        void missingMeasuresAreOmitted() {
            SensorReading reading = reading(1L, AT, 24.0);   // humidité du sol seule

            PointInTimeView view = assembler.assemble(plot(), "tomate", AT,
                    readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                    PointInTimeResolver.DiagnosticChoice.NONE, List.of());

            assertThat(view.getReading().getMeasures())
                    .containsEntry("humidite_sol", 24.0)
                    .doesNotContainKey("ph")
                    .doesNotContainKey("azote");
        }

        /**
         * Un écart non nul doit être signalé : « mesures du 12 mars à 8 h 05 » et
         * « mesures les plus proches, relevées 40 minutes plus tôt » n'appellent pas la
         * même confiance.
         */
        @Test
        @DisplayName("un relevé décalé est signalé dans la réserve, avec le sens de l'écart")
        void offsetIsFlagged() {
            SensorReading reading = reading(1L, AT.minus(40, ChronoUnit.MINUTES), 24.0);

            PointInTimeView view = assembler.assemble(plot(), "tomate", AT,
                    readingPick(reading, PointInTimeResolver.SELECTION_BEFORE, -40),
                    PointInTimeResolver.DiagnosticChoice.NONE, List.of());

            assertThat(view.getReading().getOffsetMinutes()).isEqualTo(-40);
            assertThat(view.getLimitation())
                    .contains("40 minute(s) avant")
                    .contains("elles ne le décrivent pas exactement");
        }

        @Test
        @DisplayName("un relevé exact ne déclenche aucune réserve d'écart")
        void exactReadingHasNoOffsetCaveat() {
            SensorReading reading = reading(1L, AT, 24.0);

            PointInTimeView view = assembler.assemble(plot(), "tomate", AT,
                    readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                    PointInTimeResolver.DiagnosticChoice.NONE, List.of());

            assertThat(view.getLimitation()).doesNotContain("elles ne le décrivent pas exactement");
        }
    }

    // ============================================================
    // La réserve est inconditionnelle
    // ============================================================

    @Nested
    @DisplayName("La réserve")
    class Limitation {

        @Test
        @DisplayName("toujours renseignée, et annonce toujours que rien n'est écrit")
        void alwaysPresentAndAlwaysStatesNothingWasWritten() {
            SensorReading reading = reading(1L, AT, 24.0);

            List<PointInTimeView> views = List.of(
                    assembler.assemble(plot(), "tomate", AT,
                            readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                            diagPick(diagnostic(9L, AT, reading),
                                    PointInTimeResolver.ALIGNMENT_ON_READING, 0), List.of()),
                    assembler.assemble(plot(), "tomate", AT,
                            readingPick(reading, PointInTimeResolver.SELECTION_BEFORE, -12),
                            PointInTimeResolver.DiagnosticChoice.NONE, List.of()),
                    assembler.assemble(plot(), null, AT,
                            readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                            PointInTimeResolver.DiagnosticChoice.NONE, List.of()));

            assertThat(views).allSatisfy(view ->
                    assertThat(view.getLimitation()).isNotBlank());

            assertThat(views.get(0).getLimitation()).contains("ne laisse aucune trace");
            assertThat(views.get(1).getLimitation()).contains("ne laisse aucune trace");
        }

        /**
         * Sans culture rattachée, les moteurs agronomiques n'ont aucun seuil de
         * référence : le recalcul est vide, et le taire ferait paraître la vue plus
         * complète qu'elle ne l'est.
         */
        @Test
        @DisplayName("sans culture rattachée, la réserve l'énonce et rien n'est recalculé")
        void missingCropIsFlagged() {
            SensorReading reading = reading(1L, AT, 24.0);

            PointInTimeView view = assembler.assemble(plot(), null, AT,
                    readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                    PointInTimeResolver.DiagnosticChoice.NONE, List.of());

            assertThat(view.getNowWouldConclude()).isNull();
            assertThat(view.getLimitation()).contains("Aucune culture n'a pu être rattachée");
        }

        @Test
        @DisplayName("sans diagnostic, la réserve dit que le modèle n'est pas rejoué")
        void modelNotReplayedIsStated() {
            SensorReading reading = reading(1L, AT, 24.0);

            PointInTimeView view = assembler.assemble(plot(), "tomate", AT,
                    readingPick(reading, PointInTimeResolver.SELECTION_EXACT, 0),
                    PointInTimeResolver.DiagnosticChoice.NONE, List.of());

            assertThat(view.getLimitation())
                    .contains("n'est pas rejouée")
                    .contains("réentraîné");
        }
    }

    // ============================================================
    // Outillage
    // ============================================================

    private static PointInTimeResolver.ReadingChoice readingPick(SensorReading reading,
                                                                 String selection, int offset) {
        return new PointInTimeResolver.ReadingChoice(reading, selection, offset);
    }

    private static PointInTimeResolver.DiagnosticChoice diagPick(Diagnostic diagnostic,
                                                                 String alignment, Integer age) {
        return new PointInTimeResolver.DiagnosticChoice(diagnostic, alignment, age);
    }

    private static Plot plot() {
        Plot plot = new Plot();
        plot.setId(42L);
        plot.setName("Parcelle Nord");
        return plot;
    }

    private static SensorReading reading(Long id, Instant recordedAt, Double soilHumidity) {
        SensorReading reading = new SensorReading();
        reading.setId(id);
        reading.setRecordedAt(recordedAt);
        reading.setHumiditeSol(soilHumidity);
        reading.setAnomalyDetected(false);
        return reading;
    }

    private static Diagnostic diagnostic(Long id, Instant diagnosedAt, SensorReading reading) {
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setId(id);
        diagnostic.setDiagnosedAt(diagnosedAt);
        diagnostic.setSource("CAPTEUR");
        diagnostic.setResult("STRESS_HYDRIQUE");
        diagnostic.setConfidenceScore(0.88);
        diagnostic.setCropName("tomate");
        diagnostic.setReading(reading);
        return diagnostic;
    }

    /** Non utilisé ici, mais garde la signature de `linesOfPersisted` exercée. */
    @SuppressWarnings("unused")
    private static Recommendation persistedAdvice() {
        Recommendation recommendation = new Recommendation();
        recommendation.setContent("Irriguez.");
        recommendation.setRecommendationType("AGRONOMIQUE");
        recommendation.setPriority("HAUTE");
        recommendation.setMeasureField("humidite_sol");
        recommendation.setObservedValue(24.0);
        recommendation.setThresholdValue(35.0);
        return recommendation;
    }
}
