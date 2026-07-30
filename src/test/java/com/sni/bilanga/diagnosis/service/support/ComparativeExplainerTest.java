package com.sni.bilanga.diagnosis.service.support;

import com.sni.bilanga.diagnosis.dto.response.AlternativeComparison;
import com.sni.bilanga.diagnosis.dto.response.ClassProbability;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.DiseaseRisk;
import com.sni.bilanga.knowledge.service.interfaces.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Les quatre énoncés, et surtout le second — celui qu'il serait tentant de taire.
 *
 * <p>Chacun dit une chose différente. Les confondre sous une formule unique
 * donnerait une phrase toujours vraie et jamais informative. Le cas où les
 * mesures pencheraient pour l'alternative est celui qui compte le plus : le taire
 * serait malhonnête, puisque c'est précisément là qu'un œil humain doit
 * trancher avant de traiter.
 */
@DisplayName("ComparativeExplainer — pourquoi cette maladie, et pas l'autre ?")
class ComparativeExplainerTest {

    private KnowledgeService knowledgeService;
    private ComparativeExplainer explainer;

    private static final String CROP = "tomate";
    private static final String RETAINED = "Late_blight";
    private static final String ALTERNATIVE = "Early_blight";

    @BeforeEach
    void setUp() {
        knowledgeService = Mockito.mock(KnowledgeService.class);
        explainer = new ComparativeExplainer(knowledgeService);
    }

    // ============================================================
    // Cas 1 — les mesures départagent en faveur du retenu
    // ============================================================

    @Nested
    @DisplayName("Cas 1 — les mesures confirment le retenu")
    class MeasurementsConfirm {

        /**
         * L'argument le plus fort : deux voies indépendantes concordent. La
         * probabilité vient d'un réseau convolutif entraîné sur des images, le score
         * de risque d'un moteur déterministe appliqué à des mesures de sol. Elles
         * n'ont aucune information en commun.
         */
        @Test
        @DisplayName("l'énoncé nomme le partagé, puis le distinctif, puis les deux scores")
        void statementNamesSharedThenDistinguishing() {
            risk(RETAINED, "Mildiou", 0.82,
                    List.of("température entre 18 et 28 °C", "humidité de l'air > 85 %"));
            risk(ALTERNATIVE, "Alternariose", 0.18,
                    List.of("température entre 18 et 28 °C"));

            AlternativeComparison comparison = compareOne(0.97, 0.02);

            assertThat(comparison.getSharedConditions())
                    .containsExactly("température entre 18 et 28 °C");
            assertThat(comparison.getDistinguishingConditions())
                    .containsExactly("humidité de l'air > 85 %");
            assertThat(comparison.getRiskScore()).isEqualTo(0.18);
            assertThat(comparison.getModelProbability()).isEqualTo(0.02);
            assertThat(comparison.getDisplayName()).isEqualTo("Alternariose");

            assertThat(comparison.getStatement())
                    .contains("Mildiou retenu (97 %)")
                    .contains("plutôt que Alternariose (2 %)")
                    .contains("les deux maladies partagent")
                    .contains("température entre 18 et 28 °C")
                    .contains("mais")
                    .contains("les conditions mesurées réunissent")
                    .contains("humidité de l'air > 85 %")
                    .contains("82 %")
                    .contains("18 %");
        }

        @Test
        @DisplayName("sans condition commune, la formule saute le « partagent »")
        void noSharedConditionSkipsThatClause() {
            risk(RETAINED, "Mildiou", 0.82, List.of("humidité de l'air > 85 %"));
            risk(ALTERNATIVE, "Alternariose", 0.10, List.of("sol sec"));

            String statement = compareOne(0.97, 0.02).getStatement();

            assertThat(statement)
                    .doesNotContain("partagent")
                    .contains("les conditions mesurées réunissent");
        }
    }

    // ============================================================
    // Cas 2 — le cas qu'il serait tentant de taire
    // ============================================================

    @Nested
    @DisplayName("Cas 2 — les mesures pencheraient pour l'alternative")
    class MeasurementsDisagree {

        /**
         * <strong>Le cas le plus important de cette classe.</strong> Le modèle a
         * tranché sur l'image, mais les conditions mesurées correspondent davantage
         * à l'autre maladie. Le taire laisserait l'exploitant traiter sur la foi
         * d'une conclusion que la seconde voie contredit.
         */
        @Test
        @DisplayName("l'énoncé le dit et recommande un examen visuel")
        void disagreementIsStatedAndEscalated() {
            risk(RETAINED, "Mildiou", 0.20, List.of("humidité de l'air > 85 %"));
            risk(ALTERNATIVE, "Alternariose", 0.75,
                    List.of("température entre 24 et 30 °C", "alternance sec/humide"));

            String statement = compareOne(0.61, 0.38).getStatement();

            assertThat(statement)
                    .contains("le modèle a tranché sur l'image")
                    .contains("correspondent davantage à Alternariose")
                    .contains("75 %")
                    .contains("20 %")
                    .as("c'est le cas où un œil humain doit trancher AVANT de traiter")
                    .contains("examen visuel de confirmation est recommandé avant de traiter");
        }

        /**
         * Ce cas doit primer même quand il existe des conditions distinctives en
         * faveur du retenu : le score global est ce qui compte, sinon une seule
         * condition marginale suffirait à faire passer un désaccord pour une
         * confirmation.
         */
        @Test
        @DisplayName("il prime sur le cas 1, même s'il existe des conditions distinctives")
        void disagreementWinsOverDistinguishingConditions() {
            risk(RETAINED, "Mildiou", 0.30, List.of("humidité de l'air > 85 %", "sol frais"));
            risk(ALTERNATIVE, "Alternariose", 0.80, List.of("sol frais"));

            assertThat(compareOne(0.61, 0.38).getStatement())
                    .contains("le modèle a tranché sur l'image");
        }
    }

    // ============================================================
    // Cas 3 et 4 — les mesures ne départagent pas
    // ============================================================

    @Nested
    @DisplayName("Cas 3 et 4 — les mesures ne départagent pas")
    class MeasurementsAreInconclusive {

        /**
         * Reconnaître que la conclusion ne tient que sur l'image vaut mieux que de
         * laisser croire au contraire — c'est ce qui permet à un agronome de savoir
         * quel poids donner au diagnostic.
         */
        @Test
        @DisplayName("conditions identiques de part et d'autre → le départage est visuel")
        void identicalConditionsMeanVisualOnly() {
            List<String> same = List.of("température entre 18 et 28 °C", "feuillage humide");
            risk(RETAINED, "Mildiou", 0.55, same);
            risk(ALTERNATIVE, "Alternariose", 0.55, same);

            AlternativeComparison comparison = compareOne(0.60, 0.40);

            assertThat(comparison.getDistinguishingConditions()).isEmpty();
            assertThat(comparison.getSharedConditions()).hasSize(2);
            assertThat(comparison.getStatement())
                    .contains("conviennent aux deux")
                    .contains("repose uniquement sur l'aspect des lésions");
        }

        /**
         * Formulé différemment du cas 3 : « aucune condition connue n'est réunie »
         * n'est pas la même information que « les conditions conviennent aux deux ».
         * La première dit que la base de connaissance n'a rien vu, la seconde
         * qu'elle a vu quelque chose d'ambigu.
         */
        @Test
        @DisplayName("aucune condition réunie → énoncé distinct du cas 3")
        void noConditionAtAllHasItsOwnWording() {
            risk(RETAINED, "Mildiou", 0.0, List.of());
            risk(ALTERNATIVE, "Alternariose", 0.0, List.of());

            String statement = compareOne(0.60, 0.40).getStatement();

            assertThat(statement)
                    .contains("aucune des conditions d'apparition connues n'est réunie")
                    .doesNotContain("conviennent aux deux");
        }

        @Test
        @DisplayName("un risque introuvable ne fait pas échouer la comparaison")
        void missingRiskIsTolerated() {
            Mockito.when(knowledgeService.riskFor(eq(CROP), any(), any())).thenReturn(null);

            AlternativeComparison comparison = compareOne(0.97, 0.02);

            assertThat(comparison.getRiskScore()).isNull();
            assertThat(comparison.getDisplayName())
                    .as("à défaut de libellé, le code de la maladie fait office de nom")
                    .isEqualTo(ALTERNATIVE);
            assertThat(comparison.getStatement()).isNotBlank();
        }
    }

    // ============================================================
    // Filtres et bornes
    // ============================================================

    @Nested
    @DisplayName("Filtres")
    class Filters {

        /**
         * <strong>Sans relevé, la seconde voie n'a rien à dire.</strong> Il ne
         * resterait que la probabilité du modèle, qu'on afficherait deux fois sous
         * deux noms — une comparaison sans information.
         */
        @Test
        @DisplayName("sans relevé, aucune comparaison")
        void noReadingNoComparison() {
            assertThat(explainer.compare(CROP, RETAINED, 0.97,
                    List.of(alternative(ALTERNATIVE, 0.02)), null)).isEmpty();
        }

        @Test
        @DisplayName("sans culture, sans maladie retenue ou sans alternative, aucune comparaison")
        void missingInputsYieldEmpty() {
            SensorReading reading = new SensorReading();
            List<ClassProbability> alternatives = List.of(alternative(ALTERNATIVE, 0.02));

            assertThat(explainer.compare(null, RETAINED, 0.97, alternatives, reading)).isEmpty();
            assertThat(explainer.compare(CROP, null, 0.97, alternatives, reading)).isEmpty();
            assertThat(explainer.compare(CROP, RETAINED, 0.97, null, reading)).isEmpty();
            assertThat(explainer.compare(CROP, RETAINED, 0.97, List.of(), reading)).isEmpty();
        }

        /**
         * En deçà de 0,5 %, l'alternative est trop marginale pour mériter d'être
         * discutée : l'évoquer suggérerait un doute que le modèle n'a pas.
         */
        @Test
        @DisplayName("une alternative sous 0,5 % est écartée")
        void marginalAlternativeIsDropped() {
            risk(RETAINED, "Mildiou", 0.82, List.of("humidité de l'air > 85 %"));
            risk(ALTERNATIVE, "Alternariose", 0.10, List.of());

            assertThat(explainer.compare(CROP, RETAINED, 0.99,
                    List.of(alternative(ALTERNATIVE, 0.004)), new SensorReading())).isEmpty();
        }

        @Test
        @DisplayName("une alternative à probabilité absente n'est PAS écartée")
        void nullProbabilityIsKept() {
            risk(RETAINED, "Mildiou", 0.82, List.of("humidité de l'air > 85 %"));
            risk(ALTERNATIVE, "Alternariose", 0.10, List.of());

            assertThat(explainer.compare(CROP, RETAINED, null,
                    List.of(alternative(ALTERNATIVE, null)), new SensorReading()))
                    .as("absence de probabilité ≠ probabilité nulle : c'est le cas de /explain")
                    .hasSize(1);
        }

        @Test
        @DisplayName("la maladie retenue est écartée de ses propres alternatives")
        void retainedIsNotComparedToItself() {
            risk(RETAINED, "Mildiou", 0.82, List.of("humidité de l'air > 85 %"));

            assertThat(explainer.compare(CROP, RETAINED, 0.97,
                    List.of(alternative(RETAINED, 0.97)), new SensorReading())).isEmpty();
        }

        @Test
        @DisplayName("une alternative sans code est ignorée")
        void nullCodeIsIgnored() {
            risk(RETAINED, "Mildiou", 0.82, List.of("humidité de l'air > 85 %"));

            assertThat(explainer.compare(CROP, RETAINED, 0.97,
                    List.of(alternative(null, 0.30)), new SensorReading())).isEmpty();
        }

        /**
         * Au-delà de deux, la comparaison lasse plus qu'elle n'éclaire : la
         * quatrième alternative d'un classifieur est déjà sous le pour-cent, et la
         * justifier donnerait l'impression d'une hésitation qui n'existe pas.
         */
        @Test
        @DisplayName("au plus deux comparaisons, quel que soit le nombre d'alternatives")
        void atMostTwoComparisons() {
            risk(RETAINED, "Mildiou", 0.82, List.of("humidité de l'air > 85 %"));
            risk(ALTERNATIVE, "Alternariose", 0.10, List.of());
            risk("Leaf_Mold", "Moisissure", 0.08, List.of());
            risk("Septoria_leaf_spot", "Septoriose", 0.05, List.of());

            assertThat(explainer.compare(CROP, RETAINED, 0.80, List.of(
                            alternative(ALTERNATIVE, 0.10),
                            alternative("Leaf_Mold", 0.06),
                            alternative("Septoria_leaf_spot", 0.04)),
                    new SensorReading()))
                    .hasSize(2);
        }
    }

    // ============================================================
    // La reconstruction depuis les mesures seules
    // ============================================================

    @Nested
    @DisplayName("compareFromMeasurements — sans les probabilités du modèle")
    class FromMeasurements {

        /**
         * Les probabilités du classifieur ne sont pas conservées en base. Les
         * recalculer supposerait de relancer l'inférence sur une image qu'on n'a
         * plus, et donnerait de toute façon la réponse d'aujourd'hui, pas celle du
         * moment où le conseil a été émis. Reste la voie des mesures, exactement
         * reproductible depuis le relevé enregistré.
         */
        @Test
        @DisplayName("modelProbability est null, et les candidats viennent du moteur de risque")
        void probabilitiesAreAbsentAndCandidatesComeFromRisk() {
            SensorReading reading = new SensorReading();

            Mockito.when(knowledgeService.assessRisks(CROP, reading)).thenReturn(List.of(
                    diseaseRisk(RETAINED, "Mildiou", 0.82, List.of("humidité de l'air > 85 %")),
                    diseaseRisk(ALTERNATIVE, "Alternariose", 0.18, List.of())));

            risk(RETAINED, "Mildiou", 0.82, List.of("humidité de l'air > 85 %"));
            risk(ALTERNATIVE, "Alternariose", 0.18, List.of());

            List<AlternativeComparison> comparisons =
                    explainer.compareFromMeasurements(CROP, RETAINED, reading);

            assertThat(comparisons).hasSize(1);
            assertThat(comparisons.getFirst().getModelProbability())
                    .as("absent, et non nul : le modèle n'a pas dit « zéro », "
                            + "il n'a rien dit qu'on ait conservé")
                    .isNull();
            assertThat(comparisons.getFirst().getStatement())
                    .as("sans probabilité, l'énoncé ne doit pas afficher de pourcentage de modèle")
                    .doesNotContain("retenu (");
        }

        @Test
        @DisplayName("la maladie retenue est exclue des candidats")
        void retainedIsExcludedFromCandidates() {
            SensorReading reading = new SensorReading();

            Mockito.when(knowledgeService.assessRisks(CROP, reading)).thenReturn(List.of(
                    diseaseRisk(RETAINED, "Mildiou", 0.82, List.of())));

            assertThat(explainer.compareFromMeasurements(CROP, RETAINED, reading)).isEmpty();
        }

        @Test
        @DisplayName("sans relevé, sans culture ou sans maladie retenue → liste vide")
        void missingInputsYieldEmpty() {
            assertThat(explainer.compareFromMeasurements(CROP, RETAINED, null)).isEmpty();
            assertThat(explainer.compareFromMeasurements(null, RETAINED, new SensorReading())).isEmpty();
            assertThat(explainer.compareFromMeasurements(CROP, null, new SensorReading())).isEmpty();
        }
    }

    // ============================================================
    // Outillage
    // ============================================================

    private AlternativeComparison compareOne(double retainedProbability, Double alternativeProbability) {
        List<AlternativeComparison> comparisons = explainer.compare(CROP, RETAINED,
                retainedProbability, List.of(alternative(ALTERNATIVE, alternativeProbability)),
                new SensorReading());

        assertThat(comparisons).hasSize(1);
        return comparisons.getFirst();
    }

    private void risk(String code, String displayName, double score, List<String> conditions) {
        Mockito.when(knowledgeService.riskFor(eq(CROP), eq(code), any()))
                .thenReturn(diseaseRisk(code, displayName, score, conditions));
    }

    private static DiseaseRisk diseaseRisk(String code, String displayName,
                                          double score, List<String> conditions) {
        return DiseaseRisk.builder()
                .diseaseCode(code)
                .displayName(displayName)
                .riskScore(score)
                .satisfiedConditions(conditions)
                .build();
    }

    private static ClassProbability alternative(String code, Double probability) {
        return ClassProbability.builder()
                .diseaseCode(code)
                .probability(probability)
                .build();
    }
}
