package com.sni.bilanga.diagnosis.service.support;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.dto.response.DiagnosisReplay;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.model.Recommendation;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.knowledge.service.interfaces.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Le rejeu — et la garantie que son extraction n'a rien changé.
 *
 * <p>La classe n'avait <strong>aucun test</strong> jusqu'ici, alors qu'elle vient
 * d'être ouverte pour servir la vue « instant T » : {@code recomputeItems} est passée
 * de privée à publique, avec une signature découplée de {@code Diagnostic}. Ces tests
 * figent deux choses distinctes — que les moteurs exclus le restent, et que le chemin
 * historique traverse exactement le même code.
 */
@DisplayName("DiagnosisReplayer — rejouer la connaissance, pas le modèle")
class DiagnosisReplayerTest {

    private KnowledgeService knowledgeService;
    private DiagnosisReplayer replayer;

    private static final Instant NOW = Instant.parse("2026-07-12T08:00:00Z");

    @BeforeEach
    void setUp() {
        knowledgeService = Mockito.mock(KnowledgeService.class);
        replayer = new DiagnosisReplayer(knowledgeService,
                new ConfidenceEvaluator(knowledgeService, new BilangaProperties.Confidence()));

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

    // ============================================================
    // Les moteurs exclus doivent le rester
    // ============================================================

    @Nested
    @DisplayName("Les deux moteurs exclus, et pourquoi")
    class ExcludedEngines {

        /**
         * Météo et voisinage sont exclus <strong>délibérément</strong> : tous deux
         * rendraient l'état d'<em>aujourd'hui</em> appliqué à un relevé d'hier. L'écart
         * mesurerait alors le ciel ou les parcelles voisines, non la connaissance —
         * ce qui ruinerait l'objet même de la comparaison.
         */
        @Test
        @DisplayName("ni météo ni voisinage ne sont rejoués")
        void weatherAndNeighbourhoodAreNeverReplayed() {
            replayer.recomputeItems(plot(), "tomate", reading(), "CAPTEUR", "STRESS_HYDRIQUE");

            Mockito.verify(knowledgeService, Mockito.never()).assessWeather(any());
            Mockito.verify(knowledgeService, Mockito.never()).assessNeighbourhood(any(), any());
        }

        /**
         * Le stade n'est pas recalculé pour aujourd'hui : ce serait comparer un
         * diagnostic pris en floraison à des seuils de maturation, et l'écart
         * mesurerait le temps écoulé.
         */
        @Test
        @DisplayName("le stade passé aux moteurs est null, jamais celui d'aujourd'hui")
        void growthStageIsNeverRecomputed() {
            replayer.recomputeItems(plot(), "tomate", reading(), "CAPTEUR", "STRESS_HYDRIQUE");

            Mockito.verify(knowledgeService).analyzeAgronomic(Mockito.eq("tomate"),
                    Mockito.isNull(), any());
        }
    }

    // ============================================================
    // result == null : le cas de la vue « instant T »
    // ============================================================

    @Nested
    @DisplayName("Sans conclusion d'époque")
    class WithoutResult {

        /**
         * <strong>Le cas nominal de la vue « instant T ».</strong> Sans conclusion, il
         * n'y a rien à donner à l'étape de classification — et on ne rappelle pas le
         * microservice pour la reconstituer : appel réseau sur un chemin de lecture, et
         * modèle possiblement réentraîné.
         */
        @Test
        @DisplayName("l'étape de classification est sautée, dans les deux chaînes")
        void classificationIsSkipped() {
            replayer.recomputeItems(plot(), "tomate", reading(), "CAPTEUR", null);
            replayer.recomputeItems(plot(), "tomate", reading(), "IMAGE", null);

            Mockito.verify(knowledgeService, Mockito.never())
                    .recommendForSensorDiagnostic(any(), anyString());
            Mockito.verify(knowledgeService, Mockito.never())
                    .recommendForDisease(any(), anyString(), any());
        }

        @Test
        @DisplayName("mais les moteurs agronomiques déterministes tournent quand même")
        void deterministicEnginesStillRun() {
            replayer.recomputeItems(plot(), "tomate", reading(), null, null);

            Mockito.verify(knowledgeService).analyzeAgronomic(anyString(), any(), any());
            Mockito.verify(knowledgeService).assessRisks(anyString(), any());
            Mockito.verify(knowledgeService).assessTrends(anyString(), any(), any());
            Mockito.verify(knowledgeService).arbitrate(anyString(), any());
            Mockito.verify(knowledgeService).adaptToPlot(any(), any());
        }

        @Test
        @DisplayName("relevé ou culture absents → liste vide, aucun moteur appelé")
        void missingInputsRunNothing() {
            assertThat(replayer.recomputeItems(plot(), "tomate", null, "CAPTEUR", "X")).isEmpty();
            assertThat(replayer.recomputeItems(plot(), null, reading(), "CAPTEUR", "X")).isEmpty();

            Mockito.verifyNoInteractions(knowledgeService);
        }
    }

    // ============================================================
    // La chaîne d'origine est respectée
    // ============================================================

    @Nested
    @DisplayName("La chaîne d'origine décide de la voie rejouée")
    class ChainFidelity {

        /**
         * Rejouer l'autre voie comparerait deux pipelines différents : l'écart
         * mesurerait le changement de chaîne, pas celui de la connaissance.
         */
        @Test
        @DisplayName("IMAGE rejoue la voie maladie, CAPTEUR la voie catégorie")
        void chainIsPreserved() {
            replayer.recomputeItems(plot(), "tomate", reading(), "IMAGE", "Late_blight");
            Mockito.verify(knowledgeService).recommendForDisease("Late_blight", "tomate", reading());
            Mockito.verify(knowledgeService, Mockito.never())
                    .recommendForSensorDiagnostic(any(), anyString());

            Mockito.reset(knowledgeService);
            setUp();

            replayer.recomputeItems(plot(), "tomate", reading(), "CAPTEUR", "STRESS_HYDRIQUE");
            Mockito.verify(knowledgeService)
                    .recommendForSensorDiagnostic("STRESS_HYDRIQUE", "tomate");
            Mockito.verify(knowledgeService, Mockito.never())
                    .recommendForDisease(any(), anyString(), any());
        }
    }

    // ============================================================
    // L'extraction n'a rien changé au chemin historique
    // ============================================================

    @Nested
    @DisplayName("replay(Diagnostic, …) — inchangé par l'extraction")
    class ReplayUnchanged {

        @Test
        @DisplayName("le rejeu d'un diagnostic complet aboutit et porte sa réserve")
        void fullReplayStillWorks() {
            SensorReading reading = reading();
            Diagnostic diagnostic = diagnostic(reading);

            DiagnosisReplay replay = replayer.replay(diagnostic, List.of());

            assertThat(replay.getDiagnosticId()).isEqualTo(7L);
            assertThat(replay.getReadingId()).isEqualTo(1L);
            assertThat(replay.getOriginalDiagnosedAt()).isEqualTo(NOW);
            assertThat(replay.getLimitation())
                    .isNotBlank()
                    .contains("ne laisse aucune trace");
            assertThat(replay.getIdentical()).isTrue();
        }

        /**
         * La conclusion n'est jamais rejouée, seuls les conseils le sont : sans rappel
         * du modèle, le système n'a aucun moyen de reconclure.
         */
        @Test
        @DisplayName("result et confiance sont recopiés à l'identique des deux côtés")
        void conclusionIsNeverRecomputed() {
            DiagnosisReplay replay = replayer.replay(diagnostic(reading()), List.of());

            assertThat(replay.getOriginal().getResult())
                    .isEqualTo(replay.getReplayed().getResult())
                    .isEqualTo("STRESS_HYDRIQUE");
            assertThat(replay.getOriginal().getConfidenceScore())
                    .isEqualTo(replay.getReplayed().getConfidenceScore());
        }

        /**
         * Un diagnostic sans relevé ne peut rien rejouer. Une liste d'écarts vide se
         * lirait « rien n'a changé » — d'où un {@code kind} dédié.
         */
        @Test
        @DisplayName("un diagnostic sans relevé rend REJEU_IMPOSSIBLE, pas une liste vide")
        void diagnosticWithoutReadingIsExplicit() {
            DiagnosisReplay replay = replayer.replay(diagnostic(null), List.of());

            assertThat(replay.getDifferences())
                    .extracting(DiagnosisReplay.Difference::getKind)
                    .containsExactly("REJEU_IMPOSSIBLE");
            assertThat(replay.getSummary()).contains("Rejeu impossible");
        }
    }

    // ============================================================
    // Le diff
    // ============================================================

    @Nested
    @DisplayName("diff — quatre natures d'écart")
    class Diff {

        @Test
        @DisplayName("deux instantanés identiques ne produisent aucun écart")
        void identicalSnapshotsYieldNothing() {
            var lines = List.of(line("AGRONOMIQUE", "humidite_sol", 35.0, "HAUTE"));
            var before = replayer.snapshotOf("X", 0.9, lines);
            var after = replayer.snapshotOf("X", 0.9, lines);

            assertThat(replayer.diff(before, after, false)).isEmpty();
        }

        @Test
        @DisplayName("un seuil modifié est nommé, avec l'ancienne et la nouvelle valeur")
        void thresholdChangeIsNamed() {
            var before = replayer.snapshotOf("X", 0.9,
                    List.of(line("AGRONOMIQUE", "humidite_sol", 35.0, "HAUTE")));
            var after = replayer.snapshotOf("X", 0.9,
                    List.of(line("AGRONOMIQUE", "humidite_sol", 32.0, "HAUTE")));

            var differences = replayer.diff(before, after, false);

            assertThat(differences).hasSize(1);
            assertThat(differences.getFirst().getKind()).isEqualTo("SEUIL_MODIFIE");
            assertThat(differences.getFirst().getStatement())
                    .contains("35,00").contains("32,00").contains("humidite_sol");
        }

        @Test
        @DisplayName("une priorité modifiée est signalée à part")
        void priorityChangeIsItsOwnKind() {
            var before = replayer.snapshotOf("X", 0.9,
                    List.of(line("AGRONOMIQUE", "humidite_sol", 35.0, "MOYENNE")));
            var after = replayer.snapshotOf("X", 0.9,
                    List.of(line("AGRONOMIQUE", "humidite_sol", 35.0, "HAUTE")));

            assertThat(replayer.diff(before, after, false))
                    .extracting(DiagnosisReplay.Difference::getKind)
                    .containsExactly("PRIORITE_MODIFIEE");
        }

        @Test
        @DisplayName("un conseil ajouté et un conseil retiré sont distingués")
        void addedAndRemovedAreDistinguished() {
            var before = replayer.snapshotOf("X", 0.9,
                    List.of(line("AGRONOMIQUE", "humidite_sol", 35.0, "HAUTE")));
            var after = replayer.snapshotOf("X", 0.9,
                    List.of(line("RISQUE", "humidite_air", 85.0, "MOYENNE")));

            assertThat(replayer.diff(before, after, false))
                    .extracting(DiagnosisReplay.Difference::getKind)
                    .containsExactlyInAnyOrder("CONSEIL_AJOUTE", "CONSEIL_RETIRE");
        }

        /**
         * Le texte d'un conseil change dès qu'une valeur mesurée y est interpolée :
         * comparer les libellés signalerait un écart à chaque rejeu, y compris quand
         * rien n'a bougé. L'identité porte donc sur le couple type/mesure.
         */
        @Test
        @DisplayName("un libellé reformulé, à seuil égal, ne produit AUCUN écart")
        void rewordingAloneIsNotADifference() {
            var before = replayer.snapshotOf("X", 0.9, List.of(
                    DiagnosisReplay.Snapshot.Line.builder()
                            .content("L'humidité du sol vaut 24,00.")
                            .type("AGRONOMIQUE").priority("HAUTE")
                            .measureField("humidite_sol").thresholdValue(35.0).build()));
            var after = replayer.snapshotOf("X", 0.9, List.of(
                    DiagnosisReplay.Snapshot.Line.builder()
                            .content("Humidité du sol mesurée à 24,00 — sous le seuil.")
                            .type("AGRONOMIQUE").priority("HAUTE")
                            .measureField("humidite_sol").thresholdValue(35.0).build()));

            assertThat(replayer.diff(before, after, false)).isEmpty();
        }
    }

    // ============================================================
    // Outillage
    // ============================================================

    private static DiagnosisReplay.Snapshot.Line line(String type, String measureField,
                                                      Double threshold, String priority) {
        return DiagnosisReplay.Snapshot.Line.builder()
                .content("Conseil sur " + measureField)
                .type(type)
                .priority(priority)
                .measureField(measureField)
                .observedValue(24.0)
                .thresholdValue(threshold)
                .build();
    }

    private static Plot plot() {
        Plot plot = new Plot();
        plot.setId(42L);
        plot.setName("Parcelle Nord");
        return plot;
    }

    private static SensorReading reading() {
        SensorReading reading = new SensorReading();
        reading.setId(1L);
        reading.setRecordedAt(NOW);
        reading.setHumiditeSol(24.0);
        return reading;
    }

    private static Diagnostic diagnostic(SensorReading reading) {
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setId(7L);
        diagnostic.setPlot(plot());
        diagnostic.setReading(reading);
        diagnostic.setSource("CAPTEUR");
        diagnostic.setResult("STRESS_HYDRIQUE");
        diagnostic.setConfidenceScore(0.88);
        diagnostic.setCropName("tomate");
        diagnostic.setDiagnosedAt(NOW);
        return diagnostic;
    }

    /** Non exercé ici — garde la signature de linesOfPersisted compilée. */
    @SuppressWarnings("unused")
    private static Recommendation persisted() {
        return new Recommendation();
    }
}
