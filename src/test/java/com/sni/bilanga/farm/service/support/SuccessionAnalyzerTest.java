package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.CropClosureReason;
import com.sni.bilanga.farm.dto.response.CropComparison;
import com.sni.bilanga.farm.dto.response.PlotSuccession;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lire une parcelle comme une suite, et non comme un tas.
 *
 * <p>Trois propriétés à figer : une erreur de saisie ne compte pas dans l'histoire
 * (elle n'a jamais occupé le sol), une fin <em>estimée</em> se signale comme telle, et
 * une comparaison sans campagne antérieure produit une <em>phrase</em> plutôt qu'un
 * tableau vide.
 */
@DisplayName("SuccessionAnalyzer — l'histoire agronomique d'une parcelle")
class SuccessionAnalyzerTest {

    private final SuccessionAnalyzer analyzer = new SuccessionAnalyzer();

    // ============================================================
    // La suite
    // ============================================================

    @Nested
    @DisplayName("La chronologie")
    class Chronology {

        @Test
        @DisplayName("les campagnes sont rendues de la plus récente à la plus ancienne")
        void campaignsKeepRepositoryOrder() {
            PlotSuccession succession = analyzer.analyze(plot(), List.of(
                    closed("tomate", "2026-04-01", "2026-08-01"),
                    closed("manioc", "2025-01-01", "2025-11-01")));

            assertThat(succession.getCampaignCount()).isEqualTo(2);
            assertThat(succession.getCampaigns())
                    .extracting(PlotSuccession.Campaign::getCropName)
                    .containsExactly("TOMATE", "MANIOC");
        }

        @Test
        @DisplayName("la durée et l'intervalle depuis la précédente sont calculés")
        void durationAndGapAreComputed() {
            PlotSuccession succession = analyzer.analyze(plot(), List.of(
                    closed("tomate", "2026-04-01", "2026-08-01"),
                    closed("manioc", "2025-01-01", "2025-11-01")));

            PlotSuccession.Campaign recent = succession.getCampaigns().getFirst();

            assertThat(recent.getDurationDays()).isEqualTo(122);
            assertThat(recent.getDaysSincePrevious())
                    .as("du 1er novembre 2025 au 1er avril 2026")
                    .isEqualTo(151);

            assertThat(succession.getCampaigns().get(1).getDaysSincePrevious())
                    .as("la plus ancienne n'a pas de précédente")
                    .isNull();
        }

        /**
         * <strong>La distinction qui compte.</strong> Un intervalle calculé sur une date
         * de récolte <em>prévue</em> n'a pas la même valeur qu'un intervalle calculé sur
         * un constat. Le taire ferait passer une projection pour un fait.
         */
        @Test
        @DisplayName("une fin ESTIMÉE est signalée, et expliquée dans missingData")
        void estimatedEndDateIsFlagged() {
            Crop open = crop("tomate", "2026-04-01");
            open.setExpectedHarvestDate(LocalDate.parse("2026-08-01"));

            PlotSuccession succession = analyzer.analyze(plot(), List.of(open));

            PlotSuccession.Campaign campaign = succession.getCampaigns().getFirst();
            assertThat(campaign.getEndDateIsEstimated()).isTrue();
            assertThat(campaign.getEndDate()).isEqualTo(LocalDate.parse("2026-08-01"));

            assertThat(succession.getMissingData())
                    .anySatisfy(message -> assertThat(message)
                            .contains("pas de date de fin réelle")
                            .contains("approximatifs"));
        }

        @Test
        @DisplayName("une fin CONSTATÉE ne déclenche aucune réserve")
        void actualEndDateIsNotFlagged() {
            PlotSuccession succession = analyzer.analyze(plot(),
                    List.of(closed("tomate", "2026-04-01", "2026-08-01")));

            assertThat(succession.getCampaigns().getFirst().getEndDateIsEstimated()).isFalse();
            assertThat(succession.getMissingData()).isEmpty();
        }

        /**
         * Une erreur de saisie n'a jamais occupé le sol. La compter fausserait le
         * précédent cultural et fabriquerait une jachère qui n'a pas existé.
         */
        @Test
        @DisplayName("une campagne close pour ERREUR_DE_SAISIE est EXCLUE de l'histoire")
        void dataEntryErrorIsExcluded() {
            Crop bogus = closed("manioc", "2026-01-01", "2026-02-01");
            bogus.setClosureReason(CropClosureReason.ERREUR_DE_SAISIE.name());

            PlotSuccession succession = analyzer.analyze(plot(), List.of(
                    closed("tomate", "2026-04-01", "2026-08-01"),
                    bogus));

            assertThat(succession.getCampaignCount()).isEqualTo(1);
            assertThat(succession.getCampaigns())
                    .extracting(PlotSuccession.Campaign::getCropName)
                    .containsExactly("TOMATE");
        }

        @Test
        @DisplayName("les autres motifs de clôture restent dans l'histoire")
        void otherClosureReasonsRemain() {
            Crop abandoned = closed("manioc", "2025-01-01", "2025-06-01");
            abandoned.setClosureReason(CropClosureReason.ABANDON.name());

            assertThat(analyzer.analyze(plot(), List.of(abandoned)).getCampaignCount())
                    .as("un abandon a bien occupé le sol et consommé des intrants")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("aucune campagne → réponse vide mais complète, sans exception")
        void emptyHistoryIsHandled() {
            PlotSuccession succession = analyzer.analyze(plot(), List.of());

            assertThat(succession.getCampaignCount()).isZero();
            assertThat(succession.getCampaigns()).isEmpty();
            assertThat(succession.getFallowPeriods()).isEmpty();
            assertThat(succession.getMonocultureWarnings()).isEmpty();
            assertThat(succession.getLimitation()).isNotBlank();

            assertThat(analyzer.analyze(plot(), null).getCampaignCount()).isZero();
        }
    }

    // ============================================================
    // Sol nu
    // ============================================================

    @Nested
    @DisplayName("Intervalles de sol nu")
    class Fallow {

        @Test
        @DisplayName("un intervalle long est signalé, avec ses deux cultures")
        void longGapIsReported() {
            PlotSuccession succession = analyzer.analyze(plot(), List.of(
                    closed("tomate", "2026-04-01", "2026-08-01"),
                    closed("manioc", "2025-01-01", "2025-11-01")));

            assertThat(succession.getFallowPeriods()).hasSize(1);

            PlotSuccession.FallowPeriod fallow = succession.getFallowPeriods().getFirst();
            assertThat(fallow.getDays()).isEqualTo(151);
            assertThat(fallow.getPreviousCrop()).isEqualTo("MANIOC");
            assertThat(fallow.getNextCrop()).isEqualTo("TOMATE");
        }

        /**
         * En deçà de trois semaines, ce n'est pas une jachère : c'est le temps de
         * préparer le sol. Le signaler noierait les vrais repos sous du bruit.
         */
        @Test
        @DisplayName("un intervalle court n'est PAS une jachère")
        void shortGapIsNotFallow() {
            PlotSuccession succession = analyzer.analyze(plot(), List.of(
                    closed("tomate", "2026-08-10", "2026-12-01"),
                    closed("manioc", "2025-01-01", "2026-08-01")));

            assertThat(succession.getFallowPeriods())
                    .as("neuf jours, c'est la préparation du sol")
                    .isEmpty();
        }

        /**
         * Un chevauchement est une incohérence de saisie qu'il faut voir. La masquer à
         * zéro ferait disparaître le seul indice qu'on en a.
         */
        @Test
        @DisplayName("un chevauchement donne un intervalle négatif, signalé et non écrasé")
        void overlapProducesNegativeGap() {
            PlotSuccession succession = analyzer.analyze(plot(), List.of(
                    closed("tomate", "2026-01-01", "2026-06-01"),
                    closed("manioc", "2025-01-01", "2026-03-01")));

            assertThat(succession.getCampaigns().getFirst().getDaysSincePrevious())
                    .isNegative();
            assertThat(succession.getFallowPeriods())
                    .as("un chevauchement n'est pas une jachère")
                    .isEmpty();
        }

        @Test
        @DisplayName("une campagne sans date ne fait pas échouer le calcul")
        void missingDatesAreTolerated() {
            Crop undated = crop("tomate", null);

            PlotSuccession succession = analyzer.analyze(plot(), List.of(
                    undated, closed("manioc", "2025-01-01", "2025-06-01")));

            assertThat(succession.getCampaigns().getFirst().getDurationDays()).isNull();
            assertThat(succession.getFallowPeriods()).isEmpty();
        }
    }

    // ============================================================
    // Monoculture
    // ============================================================

    @Nested
    @DisplayName("Monoculture")
    class Monoculture {

        /**
         * Signal agronomique réel : la monoculture épuise les mêmes réserves du sol et
         * concentre les ravageurs propres à l'espèce. Le système avait l'information
         * depuis toujours sans jamais la dire.
         */
        @Test
        @DisplayName("la même culture deux campagnes de suite est signalée")
        void repeatedCropIsWarned() {
            PlotSuccession succession = analyzer.analyze(plot(), List.of(
                    closed("tomate", "2026-04-01", "2026-08-01"),
                    closed("tomate", "2025-04-01", "2025-08-01")));

            assertThat(succession.getMonocultureWarnings()).hasSize(1);
            assertThat(succession.getMonocultureWarnings().getFirst())
                    .contains("TOMATE")
                    .contains("2 campagnes de suite")
                    .contains("rotation");
        }

        @Test
        @DisplayName("trois campagnes de suite comptent trois, pas deux fois deux")
        void streakIsCountedNotDoubled() {
            PlotSuccession succession = analyzer.analyze(plot(), List.of(
                    closed("tomate", "2027-04-01", "2027-08-01"),
                    closed("tomate", "2026-04-01", "2026-08-01"),
                    closed("tomate", "2025-04-01", "2025-08-01")));

            assertThat(succession.getMonocultureWarnings()).hasSize(1);
            assertThat(succession.getMonocultureWarnings().getFirst()).contains("3 campagnes");
        }

        @Test
        @DisplayName("une rotation ne déclenche aucun avertissement")
        void rotationIsSilent() {
            PlotSuccession succession = analyzer.analyze(plot(), List.of(
                    closed("tomate", "2026-04-01", "2026-08-01"),
                    closed("manioc", "2025-01-01", "2025-11-01"),
                    closed("tomate", "2024-04-01", "2024-08-01")));

            assertThat(succession.getMonocultureWarnings()).isEmpty();
        }
    }

    // ============================================================
    // Comparaison N vs N-1
    // ============================================================

    @Nested
    @DisplayName("Comparaison avec la campagne précédente")
    class Comparison {

        /**
         * Une phrase, jamais un tableau vide : « première campagne de cette culture » est
         * une information, un blanc ne l'est pas.
         */
        @Test
        @DisplayName("sans campagne antérieure, une PHRASE l'énonce")
        void noPreviousProducesAStatement() {
            CropComparison comparison = analyzer.compare(plot(),
                    withEconomics(closed("tomate", "2026-04-01", "2026-08-01"), 2300.0, 759375.0),
                    null);

            assertThat(comparison.getComparable()).isFalse();
            assertThat(comparison.getPrevious()).isNull();
            assertThat(comparison.getMetrics()).isEmpty();
            assertThat(comparison.getSummary())
                    .contains("Première campagne de TOMATE")
                    .contains("pas de campagne antérieure");
            assertThat(comparison.getLimitation()).isNotBlank();
        }

        @Test
        @DisplayName("deux bilans figés produisent des écarts chiffrés")
        void twoFrozenBudgetsProduceMetrics() {
            CropComparison comparison = analyzer.compare(plot(),
                    withEconomics(closed("tomate", "2026-04-01", "2026-08-01"), 2300.0, 759375.0),
                    withEconomics(closed("tomate", "2025-04-01", "2025-08-01"), 1900.0, 600000.0));

            assertThat(comparison.getComparable()).isTrue();
            assertThat(comparison.getMetrics()).isNotEmpty();

            CropComparison.Metric yield = comparison.getMetrics().stream()
                    .filter(m -> "yieldPerHectare".equals(m.getKey()))
                    .findFirst().orElseThrow();

            assertThat(yield.getPreviousValue()).isEqualTo(1900.0);
            assertThat(yield.getCurrentValue()).isEqualTo(2300.0);
            assertThat(yield.getChange()).isEqualTo(400.0);
            assertThat(yield.getChangePercent()).isEqualTo(21.05);
            assertThat(yield.getBetter()).isTrue();
            assertThat(yield.getStatement()).contains("2300,0").contains("1900,0");
        }

        /**
         * Les charges ne sont pas orientées : elles peuvent monter pour de bonnes
         * raisons — un traitement de plus qui sauve la récolte. Les marquer « moins
         * bien » serait un jugement que la donnée ne soutient pas.
         */
        @Test
        @DisplayName("les charges n'ont PAS de sens souhaitable, même en variant")
        void costsAreNotDirectional() {
            Crop current = closed("tomate", "2026-04-01", "2026-08-01");
            current.setEconomicsSnapshot(Map.of("totalCost", 312500.0, "yieldPerHectare", 2300.0));

            Crop previous = closed("tomate", "2025-04-01", "2025-08-01");
            previous.setEconomicsSnapshot(Map.of("totalCost", 180000.0, "yieldPerHectare", 1900.0));

            CropComparison comparison = analyzer.compare(plot(), current, previous);

            CropComparison.Metric cost = comparison.getMetrics().stream()
                    .filter(m -> "totalCost".equals(m.getKey()))
                    .findFirst().orElseThrow();

            assertThat(cost.getChange())
                    .as("l'écart est bien calculé")
                    .isEqualTo(132500.0);
            assertThat(cost.getBetter())
                    .as("des charges qui montent peuvent sauver une récolte")
                    .isNull();

            assertThat(comparison.getMetrics().stream()
                    .filter(m -> "yieldPerHectare".equals(m.getKey()))
                    .findFirst().orElseThrow().getBetter())
                    .as("le rendement, lui, est orienté")
                    .isTrue();
        }

        @Test
        @DisplayName("un bilan figé manquant d'un côté est expliqué, pas silencieux")
        void missingFrozenBudgetIsExplained() {
            CropComparison comparison = analyzer.compare(plot(),
                    withEconomics(closed("tomate", "2026-04-01", "2026-08-01"), 2300.0, 759375.0),
                    closed("tomate", "2025-04-01", "2025-08-01"));   // sans bilan

            assertThat(comparison.getComparable()).isTrue();
            assertThat(comparison.getMetrics()).isEmpty();
            assertThat(comparison.getMissingData())
                    .anySatisfy(message -> assertThat(message)
                            .contains("pas de bilan arrêté"));
            assertThat(comparison.getSummary()).contains("Aucun indicateur économique");
        }

        /**
         * Diviser par zéro afficherait « +∞ », ce qui ne se lit pas. Un pourcentage
         * inventé serait pire que son absence.
         */
        @Test
        @DisplayName("une valeur antérieure nulle ne produit pas de pourcentage")
        void zeroBaselineYieldsNoPercentage() {
            CropComparison comparison = analyzer.compare(plot(),
                    withEconomics(closed("tomate", "2026-04-01", "2026-08-01"), 2300.0, 100.0),
                    withEconomics(closed("tomate", "2025-04-01", "2025-08-01"), 0.0, 0.0));

            CropComparison.Metric yield = comparison.getMetrics().stream()
                    .filter(m -> "yieldPerHectare".equals(m.getKey()))
                    .findFirst().orElseThrow();

            assertThat(yield.getChange()).isEqualTo(2300.0);
            assertThat(yield.getChangePercent()).isNull();
        }

        /**
         * Les montants du bilan figé sont passés par {@code jsonb} et en sont ressortis
         * sous forme de chaînes. Ne pas les accepter rendrait toute comparaison vide.
         */
        @Test
        @DisplayName("les montants figés en CHAÎNES sont correctement relus")
        void stringAmountsAreParsed() {
            Crop current = closed("tomate", "2026-04-01", "2026-08-01");
            current.setEconomicsSnapshot(Map.of("yieldPerHectare", "2300.0"));

            Crop previous = closed("tomate", "2025-04-01", "2025-08-01");
            previous.setEconomicsSnapshot(Map.of("yieldPerHectare", "1900.00"));

            CropComparison comparison = analyzer.compare(plot(), current, previous);

            assertThat(comparison.getMetrics()).isNotEmpty();
            assertThat(comparison.getMetrics().getFirst().getChange()).isEqualTo(400.0);
        }

        @Test
        @DisplayName("la réserve est toujours renseignée")
        void limitationIsAlwaysPresent() {
            List<CropComparison> cases = List.of(
                    analyzer.compare(plot(), closed("tomate", "2026-04-01", "2026-08-01"), null),
                    analyzer.compare(plot(),
                            withEconomics(closed("tomate", "2026-04-01", "2026-08-01"), 2300.0, 1.0),
                            withEconomics(closed("tomate", "2025-04-01", "2025-08-01"), 1900.0, 1.0)));

            assertThat(cases).allSatisfy(comparison -> assertThat(comparison.getLimitation())
                    .isNotBlank()
                    .contains("n'en donne pas la cause"));
        }
    }

    // ============================================================
    // Fabriques
    // ============================================================

    private static Plot plot() {
        Plot plot = new Plot();
        plot.setId(42L);
        plot.setName("Parcelle Nord");
        plot.setPlotCode("PARC-2026-000014");
        return plot;
    }

    private static Crop crop(String cropName, String plantingDate) {
        Crop crop = new Crop();
        crop.setId(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 100_000));
        crop.setCropName(cropName);
        crop.setPlantingDate(plantingDate == null ? null : LocalDate.parse(plantingDate));
        crop.setStatus("EN_COURS");
        return crop;
    }

    private static Crop closed(String cropName, String plantingDate, String endDate) {
        Crop crop = crop(cropName, plantingDate);
        crop.setActualEndDate(LocalDate.parse(endDate));
        crop.setStatus("TERMINEE");
        crop.setClosureReason(CropClosureReason.RECOLTE_NORMALE.name());
        return crop;
    }

    private static Crop withEconomics(Crop crop, double yieldPerHectare, double marginPerHectare) {
        crop.setEconomicsSnapshot(Map.of(
                "yieldPerHectare", yieldPerHectare,
                "marginPerHectare", marginPerHectare,
                "grossRevenue", "920000.00",
                "totalCost", "312500.00",
                "uptakeRate", 61.7));
        return crop;
    }
}
