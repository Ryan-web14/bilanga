package com.sni.bilanga.harvest.service.support;

import com.sni.bilanga.diagnosis.repository.RecommendationRepository;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.harvest.dto.response.PlotEconomics;
import com.sni.bilanga.harvest.model.Harvest;
import com.sni.bilanga.harvest.repository.HarvestRepository;
import com.sni.bilanga.intervention.repository.InterventionRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * Les trois divisions qui peuvent mal tourner, et le refus de taire un vide.
 *
 * <p>Ce calcul est celui qui <em>quantifie</em> l'apport de la plateforme. Un
 * chiffre faux y est plus dommageable qu'ailleurs : il n'est pas visiblement
 * absurde, il est simplement optimiste — et rien ne le distingue d'un chiffre
 * juste. D'où {@code missingData}, qui doit toujours dire pourquoi un total est
 * bas.
 */
@DisplayName("MarginCalculator — un total sans réserve serait lu comme un résultat")
class MarginCalculatorTest {

    private HarvestRepository harvestRepository;
    private InterventionRepository interventionRepository;
    private RecommendationRepository recommendationRepository;
    private MarginCalculator calculator;

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @BeforeEach
    void setUp() {
        harvestRepository = Mockito.mock(HarvestRepository.class);
        interventionRepository = Mockito.mock(InterventionRepository.class);
        recommendationRepository = Mockito.mock(RecommendationRepository.class);

        calculator = new MarginCalculator(
                harvestRepository, interventionRepository, recommendationRepository);

        harvests(List.of());
        costs(List.of());
        uptake(null);
    }

    // ============================================================
    // Le cas nominal
    // ============================================================

    @Nested
    @DisplayName("Cas nominal")
    class Nominal {

        @Test
        @DisplayName("produit brut = Σ quantité × prix unitaire, marge = produit − charges")
        void computesRevenueCostAndMargin() {
            harvests(List.of(
                    harvest(1000d, "kg", new BigDecimal("500"), "XAF"),
                    harvest(840d, "kg", new BigDecimal("500"), "XAF")));
            costs(List.<Object[]>of(
                    new Object[]{"FERTILISATION", new BigDecimal("180000"), 5L},
                    new Object[]{"TRAITEMENT", new BigDecimal("92500"), 3L},
                    new Object[]{"IRRIGATION", new BigDecimal("40000"), 3L}));
            uptake(new Object[]{47, 29});

            PlotEconomics economics = calculator.compute(plot(), crop(0.8), FROM, TO);

            assertThat(economics.getGrossRevenue()).isEqualByComparingTo("920000.00");
            assertThat(economics.getTotalCost()).isEqualByComparingTo("312500.00");
            assertThat(economics.getMargin()).isEqualByComparingTo("607500.00");
            assertThat(economics.getHarvestCount()).isEqualTo(2);
            assertThat(economics.getInterventionCount()).isEqualTo(11);
            assertThat(economics.getTotalQuantity()).isEqualTo(1840d);
            assertThat(economics.getQuantityUnit()).isEqualTo("kg");
            assertThat(economics.getCurrency()).isEqualTo("XAF");

            assertThat(economics.getMarginPerHectare()).isEqualByComparingTo("759375.00");
            assertThat(economics.getYieldPerHectare()).isEqualTo(2300d);
            assertThat(economics.getUptakeRate()).isEqualTo(61.7);
        }

        /**
         * Les charges sont libellées avec l'étiquette lisible de l'énumération, non
         * avec la constante stockée : c'est le résumé et l'écran qui les consomment.
         */
        @Test
        @DisplayName("les charges sont ventilées par type, avec un libellé lisible")
        void costsAreLabelled() {
            costs(List.<Object[]>of(new Object[]{"FERTILISATION", new BigDecimal("180000"), 5L}));

            assertThat(calculator.compute(plot(), crop(1d), FROM, TO).getCostByInterventionType())
                    .containsEntry("Fertilisation", new BigDecimal("180000.00"));
        }

        @Test
        @DisplayName("un type de charge hors vocabulaire garde sa valeur brute")
        void unknownCostTypeKeepsRawValue() {
            costs(List.<Object[]>of(new Object[]{"BRICOLAGE", new BigDecimal("1000"), 1L}));

            assertThat(calculator.compute(plot(), crop(1d), FROM, TO).getCostByInterventionType())
                    .containsKey("BRICOLAGE");
        }

        @Test
        @DisplayName("une marge négative est annoncée comme telle, pas masquée")
        void negativeMarginIsStated() {
            harvests(List.of(harvest(10d, "kg", new BigDecimal("100"), "XAF")));
            costs(List.<Object[]>of(new Object[]{"TRAITEMENT", new BigDecimal("50000"), 2L}));

            PlotEconomics economics = calculator.compute(plot(), crop(1d), FROM, TO);

            assertThat(economics.getMargin()).isNegative();
            assertThat(economics.getSummary()).contains("NÉGATIVE");
        }
    }

    // ============================================================
    // Les vides, signalés
    // ============================================================

    @Nested
    @DisplayName("missingData — pourquoi un chiffre est bas")
    class MissingData {

        /**
         * <strong>Le cas le plus trompeur du calcul.</strong> Aucune intervention
         * saisie ⇒ charges nulles ⇒ marge égale au produit brut. Le chiffre paraît
         * excellent, et rien ne le distingue d'une exploitation réellement
         * performante. Le signaler est la seule protection.
         */
        @Test
        @DisplayName("aucune intervention → les charges nulles sont dénoncées comme une absence de saisie")
        void noInterventionsIsFlagged() {
            harvests(List.of(harvest(1000d, "kg", new BigDecimal("500"), "XAF")));
            costs(List.of());

            PlotEconomics economics = calculator.compute(plot(), crop(1d), FROM, TO);

            assertThat(economics.getTotalCost()).isEqualByComparingTo("0.00");
            assertThat(economics.getMissingData())
                    .anySatisfy(message -> assertThat(message)
                            .contains("Aucune intervention")
                            .contains("surestimée"));
        }

        @Test
        @DisplayName("aucune récolte → le produit nul est expliqué")
        void noHarvestIsFlagged() {
            harvests(List.of());

            assertThat(calculator.compute(plot(), crop(1d), FROM, TO).getMissingData())
                    .anySatisfy(message -> assertThat(message)
                            .contains("Aucune récolte")
                            .contains("absence de donnée"));
        }

        /**
         * Une récolte sans prix est comptée pour zéro <em>et signalée</em> :
         * l'ignorer silencieusement donnerait une marge fausse que rien ne
         * distinguerait d'une marge juste.
         */
        @Test
        @DisplayName("une récolte sans prix est écartée du produit ET signalée")
        void unpricedHarvestIsExcludedAndFlagged() {
            harvests(List.of(
                    harvest(1000d, "kg", new BigDecimal("500"), "XAF"),
                    harvest(500d, "kg", null, "XAF")));

            PlotEconomics economics = calculator.compute(plot(), crop(1d), FROM, TO);

            assertThat(economics.getGrossRevenue())
                    .as("seule la récolte tarifée entre dans le produit")
                    .isEqualByComparingTo("500000.00");
            assertThat(economics.getTotalQuantity())
                    .as("mais la quantité totale reste juste")
                    .isEqualTo(1500d);
            assertThat(economics.getMissingData())
                    .anySatisfy(message -> assertThat(message)
                            .contains("sans prix unitaire")
                            .contains("sous-estimé"));
        }

        @Test
        @DisplayName("une récolte sans quantité est également écartée et signalée")
        void unquantifiedHarvestIsFlagged() {
            harvests(List.of(harvest(null, "kg", new BigDecimal("500"), "XAF")));

            PlotEconomics economics = calculator.compute(plot(), crop(1d), FROM, TO);

            assertThat(economics.getGrossRevenue()).isEqualByComparingTo("0.00");
            assertThat(economics.getMissingData())
                    .anySatisfy(message -> assertThat(message).contains("sans quantité"));
        }

        /**
         * <strong>La réserve n'est pas conditionnelle.</strong> Le rapprochement
         * « conseils suivis / rendement » est descriptif, jamais causal — et un jury
         * le demanderait de toute façon.
         */
        @Test
        @DisplayName("limitation est TOUJOURS renseignée, même sur un bilan complet")
        void limitationIsAlwaysPresent() {
            harvests(List.of(harvest(1000d, "kg", new BigDecimal("500"), "XAF")));
            costs(List.<Object[]>of(new Object[]{"IRRIGATION", new BigDecimal("40000"), 3L}));
            uptake(new Object[]{10, 10});

            PlotEconomics economics = calculator.compute(plot(), crop(1d), FROM, TO);

            assertThat(economics.getMissingData()).isEmpty();
            assertThat(economics.getLimitation())
                    .isNotBlank()
                    .contains("constat, pas une démonstration");
        }
    }

    // ============================================================
    // Les trois divisions
    // ============================================================

    @Nested
    @DisplayName("Les trois divisions qui peuvent mal tourner")
    class Divisions {

        /**
         * Diviser par zéro donnerait l'infini, et afficher « charges à l'infini »
         * pour une parcelle simplement pas encore récoltée serait absurde.
         */
        @Test
        @DisplayName("produit nul → costRatio est null, jamais l'infini")
        void zeroRevenueYieldsNullCostRatio() {
            harvests(List.of());
            costs(List.<Object[]>of(new Object[]{"IRRIGATION", new BigDecimal("40000"), 3L}));

            assertThat(calculator.compute(plot(), crop(1d), FROM, TO).getCostRatio()).isNull();
        }

        @Test
        @DisplayName("costRatio est le pourcentage du produit absorbé par les charges")
        void costRatioIsAPercentage() {
            harvests(List.of(harvest(1000d, "kg", new BigDecimal("1000"), "XAF")));
            costs(List.<Object[]>of(new Object[]{"IRRIGATION", new BigDecimal("250000"), 3L}));

            assertThat(calculator.compute(plot(), crop(1d), FROM, TO).getCostRatio())
                    .isEqualTo(25.0);
        }

        /**
         * Ce sont les seuls chiffres comparables entre parcelles : sans surface,
         * deux parcelles ne se comparent pas, et il faut le dire plutôt que de
         * livrer un {@code null} muet.
         */
        @Test
        @DisplayName("surface absente → marge/ha et rendement/ha à null, et le vide est expliqué")
        void missingAreaYieldsNullRatiosAndAnExplanation() {
            harvests(List.of(harvest(1000d, "kg", new BigDecimal("500"), "XAF")));

            PlotEconomics economics = calculator.compute(plot(), crop(null), FROM, TO);

            assertThat(economics.getMarginPerHectare()).isNull();
            assertThat(economics.getYieldPerHectare()).isNull();
            assertThat(economics.getMissingData())
                    .anySatisfy(message -> assertThat(message)
                            .contains("Surface plantée non renseignée")
                            .contains("comparaison"));
        }

        @Test
        @DisplayName("une surface nulle ou négative est traitée comme absente")
        void nonPositiveAreaIsTreatedAsMissing() {
            harvests(List.of(harvest(1000d, "kg", new BigDecimal("500"), "XAF")));

            for (Double area : List.of(0d, -1d)) {
                PlotEconomics economics = calculator.compute(plot(), crop(area), FROM, TO);

                assertThat(economics.getMarginPerHectare())
                        .as("surface = %s", area)
                        .isNull();
                assertThat(economics.getYieldPerHectare()).isNull();
            }
        }

        @Test
        @DisplayName("aucun conseil sur la période → uptakeRate est null, non zéro")
        void noRecommendationYieldsNullUptake() {
            uptake(new Object[]{0, 0});

            assertThat(calculator.compute(plot(), crop(1d), FROM, TO).getUptakeRate())
                    .as("« aucun conseil » n'est pas « aucun conseil suivi »")
                    .isNull();
        }

        @Test
        @DisplayName("un uptake absent en base ne fait pas échouer le calcul")
        void nullUptakeRowIsTolerated() {
            uptake(null);

            PlotEconomics economics = calculator.compute(plot(), crop(1d), FROM, TO);

            assertThat(economics.getRecommendationCount()).isZero();
            assertThat(economics.getAppliedRecommendationCount()).isZero();
            assertThat(economics.getUptakeRate()).isNull();
        }
    }

    // ============================================================
    // Devise et unité
    // ============================================================

    @Nested
    @DisplayName("Devise et unité")
    class CurrencyAndUnit {

        @Test
        @DisplayName("sans récolte, la devise retombe sur XAF")
        void defaultsToXaf() {
            assertThat(calculator.compute(plot(), crop(1d), FROM, TO).getCurrency())
                    .isEqualTo("XAF");
        }

        @Test
        @DisplayName("la devise et l'unité viennent de la première récolte renseignée")
        void takenFromFirstNonBlank() {
            harvests(List.of(
                    harvest(10d, "  ", new BigDecimal("1"), null),
                    harvest(10d, "sacs", new BigDecimal("1"), "EUR")));

            PlotEconomics economics = calculator.compute(plot(), crop(1d), FROM, TO);

            assertThat(economics.getQuantityUnit()).isEqualTo("sacs");
            assertThat(economics.getCurrency()).isEqualTo("EUR");
        }
    }

    // ============================================================
    // Le résumé
    // ============================================================

    @Nested
    @DisplayName("Résumé rédigé")
    class Summary {

        @Test
        @DisplayName("il énonce produit, charges, marge, et signale les données manquantes")
        void summaryStatesEverything() {
            harvests(List.of(harvest(1000d, "kg", new BigDecimal("500"), "XAF")));
            uptake(new Object[]{10, 6});

            String summary = calculator.compute(plot(), crop(1d), FROM, TO).getSummary();

            assertThat(summary)
                    .contains("Produit brut")
                    .contains("500000.00")
                    .contains("XAF")
                    .contains("marge positive")
                    .contains("6 conseils sur 10")
                    .as("l'absence d'intervention doit remonter jusqu'au résumé")
                    .contains("donnée(s) manquante(s)");
        }

        @Test
        @DisplayName("le rendement n'apparaît que si la surface est connue")
        void yieldAppearsOnlyWithArea() {
            harvests(List.of(harvest(1000d, "kg", new BigDecimal("500"), "XAF")));

            Assertions.assertThat(calculator.compute(plot(), crop(null), FROM, TO).getSummary())
                    .doesNotContain("par hectare");

            Assertions.assertThat(calculator.compute(plot(), crop(0.5), FROM, TO).getSummary())
                    .contains("par hectare")
                    .contains("rendement");
        }
    }

    // ============================================================
    // Sans culture
    // ============================================================

    @Test
    @DisplayName("sans culture, le bilan reste calculable — la parcelle existe indépendamment")
    void worksWithoutCrop() {
        harvests(List.of(harvest(1000d, "kg", new BigDecimal("500"), "XAF")));

        PlotEconomics economics = calculator.compute(plot(), null, FROM, TO);

        assertThat(economics.getCropId()).isNull();
        assertThat(economics.getCropName()).isNull();
        assertThat(economics.getGrossRevenue()).isEqualByComparingTo("500000.00");
        assertThat(economics.getMarginPerHectare()).isNull();
        assertThat(economics.getGeneratedAt()).isNotNull();
    }

    // ============================================================
    // Outillage
    // ============================================================

    private void harvests(List<Harvest> harvests) {
        Mockito.when(harvestRepository.findForPeriod(anyLong(), any(), any(), any()))
                .thenReturn(harvests);
    }

    private void costs(List<Object[]> rows) {
        Mockito.when(interventionRepository.aggregateCostByType(
                        anyLong(), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(rows);
    }

    private void uptake(Object[] row) {
        Mockito.when(recommendationRepository.uptakeSummary(
                        anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(row);
    }

    private static Plot plot() {
        Plot plot = new Plot();
        plot.setId(42L);
        plot.setName("Parcelle Nord");
        plot.setPlotCode("PARC-2026-000014");
        return plot;
    }

    private static Crop crop(Double plantedArea) {
        Crop crop = new Crop();
        crop.setId(7L);
        crop.setCropName("tomate");
        crop.setPlantedArea(plantedArea);
        return crop;
    }

    private static Harvest harvest(Double quantity, String unit, BigDecimal unitPrice, String currency) {
        Harvest harvest = new Harvest();
        harvest.setQuantity(quantity);
        harvest.setUnit(unit);
        harvest.setUnitPrice(unitPrice);
        harvest.setCurrency(currency);
        return harvest;
    }
}
