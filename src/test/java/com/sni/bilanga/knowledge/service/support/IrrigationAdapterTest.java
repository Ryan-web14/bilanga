package com.sni.bilanga.knowledge.service.support;

import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reformuler sans effacer, et ne rien supposer d'une information absente.
 *
 * <p>Trois propriétés se jouent ici, et aucune ne se lit dans une signature :
 * le conseil est <em>complété</em> et non supprimé, la traçabilité survit à la
 * réécriture, et {@code null} n'est pas traité comme {@code PLUVIAL}.
 */
@DisplayName("IrrigationAdapter — rendre le conseil applicable, pas le faire disparaître")
class IrrigationAdapterTest {

    private final IrrigationAdapter adapter = new IrrigationAdapter();

    private static final String IRRIGATE =
            "Irriguez la parcelle : l'humidité du sol est descendue sous le seuil de la culture.";

    // ============================================================
    // Parcelle pluviale — le cas à reformuler
    // ============================================================

    @Nested
    @DisplayName("Sur une parcelle PLUVIAL")
    class RainfedPlot {

        /**
         * <strong>Complété, pas remplacé.</strong> Effacer le conseil ferait
         * disparaître le problème avec lui : le sol manque d'eau, ce constat reste
         * vrai. Seule la réponse change.
         */
        @Test
        @DisplayName("le conseil d'irrigation est complété d'une alternative réalisable")
        void wateringAdviceIsAugmented() {
            List<RecommendationItem> adapted =
                    adapter.adapt(plot("PLUVIAL"), List.of(item(IRRIGATE, "STRESS_HYDRIQUE")));

            String content = adapted.getFirst().getContent();

            assertThat(content)
                    .as("le constat d'origine doit subsister intégralement")
                    .startsWith(IRRIGATE);
            assertThat(content)
                    .contains("culture pluviale")
                    .contains("paillez")
                    .contains("ombrez")
                    .contains("binez");
        }

        @Test
        @DisplayName("un seul conseil de la liste est touché, les autres passent intacts")
        void onlyWateringIsRewritten() {
            RecommendationItem watering = item(IRRIGATE, "STRESS_HYDRIQUE");
            RecommendationItem nitrogen = item("Apportez de l'azote : carence marquée.", "CARENCE_N");

            List<RecommendationItem> adapted =
                    adapter.adapt(plot("PLUVIAL"), List.of(watering, nitrogen));

            assertThat(adapted).hasSize(2);
            assertThat(adapted.get(0).getContent()).isNotEqualTo(IRRIGATE);
            assertThat(adapted.get(1).getContent())
                    .as("une carence azotée n'a rien à voir avec l'irrigation")
                    .isEqualTo(nitrogen.getContent());
        }

        /**
         * <strong>La traçabilité doit survivre.</strong>
         * {@code DiagnosisExplainer} justifie un conseil depuis ces quatre
         * colonnes : les perdre en réécrivant le texte rendrait le conseil
         * injustifiable, et l'endpoint {@code /explain} muet précisément sur les
         * conseils qu'il faut le plus expliquer.
         */
        @Test
        @DisplayName("measureField, observedValue, thresholdValue et sourceRuleId survivent")
        void traceabilityIsPreserved() {
            RecommendationItem original = RecommendationItem.builder()
                    .content(IRRIGATE)
                    .type("AGRONOMIQUE")
                    .priority("HAUTE")
                    .category("STRESS_HYDRIQUE")
                    .sourceRuleId(4242L)
                    .measureField("humidite_sol")
                    .observedValue(24.0)
                    .thresholdValue(35.0)
                    .build();

            RecommendationItem adapted = adapter.adapt(plot("PLUVIAL"), List.of(original)).getFirst();

            assertThat(adapted.getSourceRuleId()).isEqualTo(4242L);
            assertThat(adapted.getMeasureField()).isEqualTo("humidite_sol");
            assertThat(adapted.getObservedValue()).isEqualTo(24.0);
            assertThat(adapted.getThresholdValue()).isEqualTo(35.0);
            assertThat(adapted.getType()).isEqualTo("AGRONOMIQUE");
            assertThat(adapted.getPriority()).isEqualTo("HAUTE");
            assertThat(adapted.getCategory()).isEqualTo("STRESS_HYDRIQUE");
        }

        /**
         * L'original ne doit pas être muté : les items viennent de moteurs qui
         * peuvent les réutiliser, et muter sur place ferait dépendre le résultat de
         * l'ordre d'appel des moteurs.
         */
        @Test
        @DisplayName("l'item d'origine n'est pas muté")
        void originalIsNotMutated() {
            RecommendationItem original = item(IRRIGATE, "STRESS_HYDRIQUE");

            adapter.adapt(plot("PLUVIAL"), List.of(original));

            assertThat(original.getContent()).isEqualTo(IRRIGATE);
        }

        /**
         * Le rattachement se fait sur la catégorie <em>et</em> sur le libellé. La
         * même catégorie porte aussi des conseils déjà compatibles avec le
         * pluvial : les réécrire produirait un doublon absurde — « paillez le sol
         * […] paillez le sol ».
         */
        @ParameterizedTest
        @ValueSource(strings = {
                "Paillez le sol pour limiter l'évaporation.",
                "Ombrez les jeunes plants aux heures les plus chaudes.",
                "Binez en surface pour rompre la remontée capillaire.",
        })
        @DisplayName("un conseil déjà compatible avec le pluvial n'est pas réécrit")
        void alreadyCompatibleAdviceIsUntouched(String content) {
            RecommendationItem item = item(content, "STRESS_HYDRIQUE");

            assertThat(adapter.adapt(plot("PLUVIAL"), List.of(item)).getFirst().getContent())
                    .isEqualTo(content);
        }

        /**
         * {@code EXCES_EAU} n'est pas dépendant de l'irrigation : évacuer l'eau ne
         * demande pas d'en apporter. Un conseil de drainage ne doit pas se voir
         * accoler une alternative de paillage.
         */
        @Test
        @DisplayName("une catégorie hors du périmètre reste intacte, même si elle dit « irriguer »")
        void outOfScopeCategoryIsUntouched() {
            RecommendationItem item = item(
                    "Cessez d'irriguer et drainez : le sol est saturé.", "EXCES_EAU");

            assertThat(adapter.adapt(plot("PLUVIAL"), List.of(item)).getFirst().getContent())
                    .isEqualTo(item.getContent());
        }

        @Test
        @DisplayName("les marqueurs sont reconnus quelle que soit la casse")
        void markersAreCaseInsensitive() {
            RecommendationItem item = item("ARROSEZ ABONDAMMENT.", "STRESS_HYDRIQUE");

            assertThat(adapter.adapt(plot("PLUVIAL"), List.of(item)).getFirst().getContent())
                    .contains("culture pluviale");
        }
    }

    // ============================================================
    // Le piège : null n'est pas PLUVIAL
    // ============================================================

    @Nested
    @DisplayName("Ne rien savoir n'autorise pas à supposer")
    class UnknownIsNotRainfed {

        /**
         * <strong>Le cas le plus important de cette classe.</strong> Ne rien
         * savoir du moyen d'irrigation n'autorise pas à supposer qu'il n'y en a
         * pas. Réécrire sur une hypothèse dirait à un exploitant équipé d'un
         * goutte-à-goutte que « l'irrigation n'est pas une option » — une
         * affirmation fausse, sur la foi d'une fiche incomplète.
         */
        @ParameterizedTest
        @NullSource
        @DisplayName("type d'irrigation absent → le conseil d'origine est conservé")
        void nullIrrigationTypeLeavesAdviceAlone(String irrigationType) {
            RecommendationItem item = item(IRRIGATE, "STRESS_HYDRIQUE");

            assertThat(adapter.adapt(plot(irrigationType), List.of(item)).getFirst().getContent())
                    .isEqualTo(IRRIGATE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "INCONNU", "n'importe quoi"})
        @DisplayName("valeur vide ou hors vocabulaire → conseil conservé")
        void unparseableIrrigationTypeLeavesAdviceAlone(String irrigationType) {
            RecommendationItem item = item(IRRIGATE, "STRESS_HYDRIQUE");

            assertThat(adapter.adapt(plot(irrigationType), List.of(item)).getFirst().getContent())
                    .isEqualTo(IRRIGATE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"GOUTTE_A_GOUTTE", "ASPERSION", "MANUEL"})
        @DisplayName("une parcelle équipée garde le conseil d'irrigation tel quel")
        void equippedPlotKeepsAdvice(String irrigationType) {
            RecommendationItem item = item(IRRIGATE, "STRESS_HYDRIQUE");

            assertThat(adapter.adapt(plot(irrigationType), List.of(item)).getFirst().getContent())
                    .isEqualTo(IRRIGATE);
        }

        @Test
        @DisplayName("la casse du type d'irrigation est indifférente")
        void irrigationTypeIsCaseInsensitive() {
            RecommendationItem item = item(IRRIGATE, "STRESS_HYDRIQUE");

            assertThat(adapter.adapt(plot("pluvial"), List.of(item)).getFirst().getContent())
                    .contains("culture pluviale");
        }
    }

    // ============================================================
    // Entrées dégénérées
    // ============================================================

    @Nested
    @DisplayName("Entrées dégénérées — jamais d'exception dans le pipeline de diagnostic")
    class Degenerate {

        @Test
        @DisplayName("parcelle nulle → la liste est rendue inchangée")
        void nullPlotReturnsInput() {
            List<RecommendationItem> items = List.of(item(IRRIGATE, "STRESS_HYDRIQUE"));

            assertThat(adapter.adapt(null, items)).isSameAs(items);
        }

        @Test
        @DisplayName("liste nulle ou vide → rendue telle quelle")
        void nullOrEmptyListIsReturned() {
            assertThat(adapter.adapt(plot("PLUVIAL"), null)).isNull();
            assertThat(adapter.adapt(plot("PLUVIAL"), List.of())).isEmpty();
        }

        @Test
        @DisplayName("un conseil sans texte ne fait pas échouer l'adaptation")
        void nullContentIsTolerated() {
            RecommendationItem item = item(null, "STRESS_HYDRIQUE");

            assertThat(adapter.adapt(plot("PLUVIAL"), List.of(item)).getFirst().getContent())
                    .isNull();
        }

        /**
         * Catégorie absente : le rattachement se fait alors sur le seul libellé.
         * C'est volontaire — un moteur qui omettrait la catégorie ne doit pas
         * échapper à l'adaptation.
         */
        @Test
        @DisplayName("catégorie absente → le libellé seul décide")
        void nullCategoryFallsBackToWording() {
            RecommendationItem item = item(IRRIGATE, null);

            assertThat(adapter.adapt(plot("PLUVIAL"), List.of(item)).getFirst().getContent())
                    .contains("culture pluviale");
        }
    }

    // ============================================================
    // Fabriques
    // ============================================================
    private static Plot plot(String irrigationType) {
        Plot plot = new Plot();
        plot.setIrrigationType(irrigationType);
        return plot;
    }

    private static RecommendationItem item(String content, String category) {
        return RecommendationItem.builder()
                .content(content)
                .type("AGRONOMIQUE")
                .priority("HAUTE")
                .category(category)
                .build();
    }
}
