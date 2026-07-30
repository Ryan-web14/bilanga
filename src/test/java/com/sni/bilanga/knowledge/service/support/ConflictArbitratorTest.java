package com.sni.bilanga.knowledge.service.support;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.knowledge.model.RecommendationArbitration;
import com.sni.bilanga.knowledge.repository.RecommendationArbitrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * L'arbitrage ne doit concilier que ce qui est réellement en conflit.
 *
 * <h2>Le défaut que ces tests figent</h2>
 *
 * <p>Le moteur se déclenchait sur la seule <strong>coprésence de catégories</strong>. Une
 * humidité du sol à 58 % quand la culture en demande 60 produit un conseil de stress
 * hydrique ; combiné à n'importe quel risque sanitaire, il déclenchait une synthèse
 * rédigée comme si les deux problèmes étaient sérieux — alors que les mesures disaient le
 * contraire de l'un des deux.
 *
 * <p><strong>Ce qui ne doit PAS changer</strong> : rien n'est jamais retiré. Les deux
 * conseils d'origine restent, et c'est l'objet du premier test de la dernière section.
 */
@DisplayName("ConflictArbitrator — ne concilier que les vrais conflits")
class ConflictArbitratorTest {

    private RecommendationArbitrationRepository repository;
    private BilangaProperties.Arbitration config;
    private ConflictArbitrator arbitrator;

    @BeforeEach
    void setUp() {
        repository = mock(RecommendationArbitrationRepository.class);
        config = new BilangaProperties.Arbitration();      // min-deviation 0.15, filtre actif
        arbitrator = new ConflictArbitrator(repository, config);

        when(repository.findForCrop(anyString())).thenReturn(List.of(
                rule("RISQUE_MALADIE", "STRESS_HYDRIQUE", "HAUTE")));
    }

    // ============================================================
    // Le filtre
    // ============================================================

    @Nested
    @DisplayName("Le filtre d'écart")
    class Filter {

        /** Le cas qui a motivé la correction. */
        @Test
        @DisplayName("un écart MARGINAL d'un côté n'arbitre plus")
        void marginalDeviationIsNotArbitrated() {
            // Humidité du sol à 58 pour un seuil de 60 : 3 % d'écart. Le conseil existe,
            // mais il ne constitue pas un problème dont il vaille la peine de discuter la
            // conciliation avec un risque sanitaire.
            List<RecommendationItem> items = List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 58.0, 60.0, "MOYENNE"),
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, "HAUTE"));

            assertThat(arbitrator.arbitrate("tomate", items))
                    .as("3 % d'écart ne justifie pas une synthèse")
                    .isEmpty();
        }

        @Test
        @DisplayName("deux écarts FRANCS arbitrent")
        void twoRealProblemsAreArbitrated() {
            List<RecommendationItem> items = List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 24.0, 60.0, "HAUTE"),
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, "HAUTE"));

            assertThat(arbitrator.arbitrate("tomate", items)).hasSize(1);
        }

        @Test
        @DisplayName("le seuil se règle, et se lève entièrement")
        void thresholdIsConfigurableAndRemovable() {
            List<RecommendationItem> marginal = List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 58.0, 60.0, "MOYENNE"),
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, "HAUTE"));

            config.setMinDeviation(0.01);
            assertThat(arbitrator.arbitrate("tomate", marginal))
                    .as("seuil abaissé : 3 % suffit désormais")
                    .hasSize(1);

            config.setMinDeviation(0.15);
            config.setRequireSignificantDeviation(false);
            assertThat(arbitrator.arbitrate("tomate", marginal))
                    .as("filtre levé : comportement d'avant, coprésence seule")
                    .hasSize(1);
        }

        /**
         * Les moteurs de risque et de tendance ne renseignent pas toujours les colonnes
         * de traçabilité. Bloquer sur leur absence rendrait le filtre plus strict
         * qu'annoncé, et ferait disparaître des arbitrages légitimes.
         */
        @Test
        @DisplayName("un écart NON MESURABLE passe le filtre, il ne le bloque pas")
        void unmeasurableDeviationPassesThrough() {
            List<RecommendationItem> items = List.of(
                    item("STRESS_HYDRIQUE", null, null, null, "HAUTE"),
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, "HAUTE"));

            assertThat(arbitrator.arbitrate("tomate", items)).hasSize(1);
        }

        @Test
        @DisplayName("un seuil NUL ne fait pas diverger le rapport")
        void zeroThresholdIsTolerated() {
            List<RecommendationItem> items = List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 24.0, 0.0, "HAUTE"),
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, "HAUTE"));

            assertThat(arbitrator.arbitrate("tomate", items)).hasSize(1);
        }

        /**
         * Deux carences, deux risques : retenir le plus marqué évite qu'un conseil mineur
         * empêche l'arbitrage d'un conflit réel signalé par un autre de la même famille.
         */
        @Test
        @DisplayName("à catégorie répétée, c'est le conseil le plus marqué qui décide")
        void mostDeviantOfACategoryDecides() {
            List<RecommendationItem> items = List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 59.0, 60.0, "BASSE"),   // 2 %
                    item("STRESS_HYDRIQUE", "humidite_sol", 24.0, 60.0, "HAUTE"),   // 60 %
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, "HAUTE"));

            assertThat(arbitrator.arbitrate("tomate", items))
                    .as("le conseil marginal ne doit pas masquer le conflit réel")
                    .hasSize(1);
        }
    }

    // ============================================================
    // La synthèse
    // ============================================================

    @Nested
    @DisplayName("Ce que porte la synthèse")
    class Synthesis {

        /**
         * L'arbitrage était le seul conseil que {@code DiagnosisExplainer} ne savait pas
         * justifier : il reconstruit les justifications depuis ces colonnes, qui
         * restaient vides.
         */
        @Test
        @DisplayName("la traçabilité est reportée — les DEUX mesures sont nommées")
        void traceabilityIsCarried() {
            RecommendationItem synthesis = arbitrator.arbitrate("tomate", List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 24.0, 60.0, "HAUTE"),
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, "HAUTE"))).getFirst();

            assertThat(synthesis.getMeasureField())
                    .contains("humidite_sol")
                    .contains("humidite_air");

            // Valeurs du côté le PLUS marqué : 60 % d'écart contre 15 %.
            assertThat(synthesis.getObservedValue()).isEqualTo(24.0);
            assertThat(synthesis.getThresholdValue()).isEqualTo(60.0);
        }

        /**
         * Concilier deux problèmes n'en rend aucun plus urgent. L'arbitrage s'auto-promouvait
         * en HAUTE — les quatre règles semées le sont toutes — et passait donc devant un
         * vrai problème isolé, le tri le plaçant en tête à priorité égale.
         */
        @Test
        @DisplayName("la priorité est celle du MOINS urgent des deux, pas celle de la règle")
        void priorityIsTheWeakestOfBoth() {
            RecommendationItem synthesis = arbitrator.arbitrate("tomate", List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 24.0, 60.0, "MOYENNE"),
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, "HAUTE"))).getFirst();

            assertThat(synthesis.getPriority())
                    .as("la règle dit HAUTE, mais un des deux côtés n'est que MOYENNE")
                    .isEqualTo("MOYENNE");
        }

        @Test
        @DisplayName("sans priorité des deux côtés, celle de la règle sert de repli")
        void ruleePriorityIsTheFallback() {
            RecommendationItem synthesis = arbitrator.arbitrate("tomate", List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 24.0, 60.0, null),
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, null))).getFirst();

            assertThat(synthesis.getPriority()).isEqualTo("HAUTE");
        }

        @Test
        @DisplayName("le type et la catégorie composée sont conservés")
        void typeAndCompositeCategoryAreKept() {
            RecommendationItem synthesis = arbitrator.arbitrate("tomate", List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 24.0, 60.0, "HAUTE"),
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, "HAUTE"))).getFirst();

            assertThat(synthesis.getType()).isEqualTo("ARBITRAGE");
            assertThat(synthesis.getCategory()).isEqualTo("RISQUE_MALADIE+STRESS_HYDRIQUE");
            assertThat(synthesis.getContent()).isNotBlank();
        }
    }

    // ============================================================
    // L'invariant
    // ============================================================

    @Nested
    @DisplayName("L'invariant : on ajoute, on ne retire jamais")
    class Invariant {

        /**
         * <strong>Le test qui protège le principe.</strong> Le moteur rend uniquement les
         * synthèses ; les conseils d'origine ne lui appartiennent pas et ne peuvent donc
         * pas disparaître. Effacer un conseil ferait disparaître le problème avec lui,
         * ce qui est pire que de proposer deux actions à concilier.
         */
        @Test
        @DisplayName("le moteur ne rend QUE des synthèses, jamais une liste amputée")
        void onlySynthesesAreReturned() {
            List<RecommendationItem> items = List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 24.0, 60.0, "HAUTE"),
                    item("RISQUE_MALADIE", "humidite_air", 92.0, 80.0, "HAUTE"));

            List<RecommendationItem> result = arbitrator.arbitrate("tomate", items);

            assertThat(result).hasSize(1);
            assertThat(result).allSatisfy(synthesis ->
                    assertThat(synthesis.getType()).isEqualTo("ARBITRAGE"));
            assertThat(items).as("la liste d'entrée n'est pas modifiée").hasSize(2);
        }

        @Test
        @DisplayName("une seule catégorie présente n'arbitre rien")
        void aSingleCategoryNeverArbitrates() {
            assertThat(arbitrator.arbitrate("tomate", List.of(
                    item("STRESS_HYDRIQUE", "humidite_sol", 24.0, 60.0, "HAUTE"),
                    item("STRESS_HYDRIQUE", "humidite_sol", 22.0, 60.0, "HAUTE"))))
                    .isEmpty();
        }

        @Test
        @DisplayName("les entrées dégénérées ne font pas échouer le moteur")
        void degenerateInputsAreTolerated() {
            assertThat(arbitrator.arbitrate(null, List.of())).isEmpty();
            assertThat(arbitrator.arbitrate("tomate", null)).isEmpty();
            assertThat(arbitrator.arbitrate("tomate", List.of())).isEmpty();
            assertThat(arbitrator.arbitrate("tomate", List.of(
                    item(null, null, null, null, null),
                    item(null, null, null, null, null)))).isEmpty();
        }
    }

    // ============================================================
    // Fabriques
    // ============================================================

    private static RecommendationArbitration rule(String a, String b, String priority) {
        RecommendationArbitration rule = new RecommendationArbitration();
        rule.setCategoryA(a);
        rule.setCategoryB(b);
        rule.setPriority(priority);
        rule.setSynthesis("Irriguez au pied, tôt le matin, afin que le feuillage soit sec.");
        return rule;
    }

    private static RecommendationItem item(String category, String measureField,
                                           Double observed, Double threshold, String priority) {
        return RecommendationItem.builder()
                .category(category)
                .measureField(measureField)
                .observedValue(observed)
                .thresholdValue(threshold)
                .priority(priority)
                .content("conseil")
                .build();
    }
}
