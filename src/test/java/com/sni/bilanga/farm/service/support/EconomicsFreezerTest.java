package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.harvest.dto.response.PlotEconomics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le bilan figé, et la divergence qui le rend honnête.
 *
 * <p>Trois propriétés à figer, chacune correspondant à un piège :
 * la portée est écrite dès le premier jour (aucune migration ne rattrape une
 * information jamais écrite) ; une divergence nulle produit une <em>phrase</em> et non
 * une liste vide ; et la comparaison est numérique, faute de quoi
 * {@code "412000.00"} contre {@code 412000.0} signalerait un écart qui n'existe pas.
 */
@DisplayName("EconomicsFreezer — figer un bilan sans mentir sur ce qu'il est")
class EconomicsFreezerTest {

    private ObjectMapper objectMapper;
    private EconomicsFreezer freezer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        freezer = new EconomicsFreezer(objectMapper);
    }

    // ============================================================
    // Le figeage
    // ============================================================

    @Nested
    @DisplayName("Figeage")
    class Freezing {

        @Test
        @DisplayName("les montants du bilan sont conservés")
        void amountsArePreserved() {
            Map<String, Object> frozen = freezer.freeze(economics("920000.00", "312500.00"));

            assertThat(frozen)
                    .containsKey("grossRevenue")
                    .containsKey("totalCost")
                    .containsKey("margin")
                    .containsKey("summary")
                    .containsKey("limitation");
        }

        /**
         * <strong>La décision qu'aucune migration future ne pourrait rattraper.</strong>
         * Le jour où une parcelle sera divisée en zones, il y aura des bilans figés de
         * parcelle et des bilans figés de zone — et rien dans la charge utile ne les
         * distinguera si le discriminant n'a pas été écrit dès le premier jour. On ne
         * déduit pas après coup une portée qui n'a jamais été enregistrée.
         */
        @Test
        @DisplayName("la PORTÉE est écrite, alors même que le zonage n'existe pas encore")
        void scopeIsWrittenFromDayOne() {
            Map<String, Object> frozen = freezer.freeze(economics("100.00", "50.00"));

            assertThat(frozen)
                    .containsEntry("scope", EconomicsFreezer.SCOPE_PLOT)
                    .containsEntry("zoneId", null);
        }

        @Test
        @DisplayName("un bilan nul rend une carte vide, jamais null")
        void nullEconomicsYieldsEmptyMap() {
            assertThat(freezer.freeze(null)).isEmpty();
        }

        /**
         * Le figeage doit être reproductible sur les mêmes entrées, sinon la comparaison
         * ultérieure signalerait des écarts imputables à la sérialisation.
         */
        @Test
        @DisplayName("figer deux fois les mêmes entrées donne la même charge utile")
        void freezingIsDeterministic() {
            PlotEconomics economics = economics("920000.00", "312500.00");

            Map<String, Object> first = freezer.freeze(economics);
            Map<String, Object> second = freezer.freeze(economics);

            assertThat(first).isEqualTo(second);
        }
    }

    // ============================================================
    // La divergence
    // ============================================================

    @Nested
    @DisplayName("Divergence")
    class Divergence {

        /**
         * <strong>Une phrase, jamais un blanc.</strong> « Identique à celui arrêté à la
         * clôture » est une information rassurante ; un tableau vide obligerait le client
         * à l'interpréter, au risque d'afficher un blanc là où il faut lire « rien n'a
         * été modifié ».
         */
        @Test
        @DisplayName("rien n'a changé → phrase explicite, pas une liste vide")
        void noChangeStillProducesAStatement() {
            PlotEconomics economics = economics("920000.00", "312500.00");
            Map<String, Object> frozen = freezer.freeze(economics);

            EconomicsFreezer.Divergence divergence = freezer.divergence(frozen, economics);

            assertThat(divergence.diverged()).isFalse();
            assertThat(divergence.changes()).isEmpty();
            assertThat(divergence.statement())
                    .isNotBlank()
                    .contains("identique")
                    .contains("rien n'a été saisi ni corrigé depuis");
        }

        /**
         * Le cas concret que cette classe attrape : la suppression d'une récolte est
         * <strong>réelle</strong> dans ce projet. Une récolte supprimée après clôture rend
         * le bilan figé faux, et cette ligne est exactement ce qui le rend visible.
         */
        @Test
        @DisplayName("un montant modifié est nommé, avec l'ancienne et la nouvelle valeur")
        void changedAmountIsNamed() {
            Map<String, Object> frozen = freezer.freeze(economics("920000.00", "312500.00"));
            PlotEconomics current = economics("880000.00", "312500.00");

            EconomicsFreezer.Divergence divergence = freezer.divergence(frozen, current);

            assertThat(divergence.diverged()).isTrue();
            assertThat(divergence.changes()).hasSize(2);   // produit brut ET marge
            assertThat(divergence.statement())
                    .contains("le produit brut")
                    .contains("920000.00")
                    .contains("880000.00")
                    .contains("Le chiffre arrêté reste la référence");
        }

        @Test
        @DisplayName("un décompte de récoltes modifié est signalé")
        void harvestCountChangeIsReported() {
            PlotEconomics before = economics("920000.00", "312500.00");
            before.setHarvestCount(3);
            PlotEconomics after = economics("920000.00", "312500.00");
            after.setHarvestCount(2);

            EconomicsFreezer.Divergence divergence =
                    freezer.divergence(freezer.freeze(before), after);

            assertThat(divergence.diverged()).isTrue();
            assertThat(divergence.statement()).contains("le nombre de récoltes");
        }

        /**
         * <strong>Le piège du faux positif.</strong> Le côté figé a fait un aller-retour
         * par {@code jsonb} et peut porter {@code "412000.00"} là où le vivant porte
         * {@code 412000.0}. Une comparaison de chaînes signalerait alors une divergence
         * inexistante — et un faux positif sur un écran d'audit apprend à ignorer le
         * signal, ce qui est pire qu'un silence.
         */
        @Test
        @DisplayName("la comparaison est NUMÉRIQUE : 412000.00 et 412000.0 sont identiques")
        void comparisonIsNumericNotTextual() {
            Map<String, Object> frozen = freezer.freeze(economics("412000.00", "0.00"));
            // Même valeur, écriture différente.
            frozen.put("grossRevenue", "412000.0");

            EconomicsFreezer.Divergence divergence =
                    freezer.divergence(frozen, economics("412000.00", "0.00"));

            assertThat(divergence.diverged())
                    .as("un écart de notation n'est pas un écart de montant")
                    .isFalse();
        }

        @Test
        @DisplayName("les champs non comparés ne déclenchent aucune divergence")
        void nonComparedFieldsAreIgnored() {
            PlotEconomics before = economics("920000.00", "312500.00");
            Map<String, Object> frozen = freezer.freeze(before);

            PlotEconomics after = economics("920000.00", "312500.00");
            after.setSummary("Un résumé rédigé différemment.");
            after.setGeneratedAt(Instant.now().plusSeconds(3600));

            assertThat(freezer.divergence(frozen, after).diverged())
                    .as("summary et generatedAt changent à chaque appel : les comparer "
                            + "noierait le signal")
                    .isFalse();
        }

        /**
         * Une campagne close par l'ancien {@code delete()} n'a pas de bilan figé. Le dire
         * vaut mieux que d'afficher « aucune divergence », qui laisserait croire à une
         * comparaison réussie.
         */
        @Test
        @DisplayName("aucun bilan figé → la phrase le dit, sans prétendre comparer")
        void missingFrozenIsStatedExplicitly() {
            EconomicsFreezer.Divergence divergence =
                    freezer.divergence(null, economics("100.00", "0.00"));

            assertThat(divergence.diverged()).isFalse();
            assertThat(divergence.statement())
                    .contains("Aucun bilan n'a été arrêté")
                    .contains("close avant que le système ne fige un bilan");

            assertThat(freezer.divergence(Map.of(), economics("100.00", "0.00")).statement())
                    .contains("Aucun bilan n'a été arrêté");
        }

        @Test
        @DisplayName("bilan actuel indisponible → la comparaison est déclarée impossible")
        void missingCurrentIsStatedExplicitly() {
            Map<String, Object> frozen = freezer.freeze(economics("100.00", "0.00"));

            EconomicsFreezer.Divergence divergence = freezer.divergence(frozen, null);

            assertThat(divergence.diverged()).isFalse();
            assertThat(divergence.statement()).contains("n'a pas pu être recalculé");
        }

        @Test
        @DisplayName("la phrase n'est JAMAIS nulle, quel que soit le cas")
        void statementIsNeverNull() {
            PlotEconomics economics = economics("100.00", "50.00");

            List<EconomicsFreezer.Divergence> cases = List.of(
                    freezer.divergence(null, economics),
                    freezer.divergence(Map.of(), economics),
                    freezer.divergence(freezer.freeze(economics), null),
                    freezer.divergence(freezer.freeze(economics), economics),
                    freezer.divergence(freezer.freeze(economics), economics("200.00", "50.00")));

            assertThat(cases).allSatisfy(divergence ->
                    assertThat(divergence.statement()).isNotBlank());
        }
    }

    // ============================================================
    // Fabrique
    // ============================================================
    private static PlotEconomics economics(String grossRevenue, String totalCost) {
        BigDecimal revenue = new BigDecimal(grossRevenue);
        BigDecimal cost = new BigDecimal(totalCost);

        return PlotEconomics.builder()
                .plotId(42L)
                .plotName("Parcelle Nord")
                .cropId(7L)
                .cropName("TOMATE")
                .from(LocalDate.of(2026, 4, 21))
                .to(LocalDate.of(2026, 8, 19))
                .currency("XAF")
                .harvestCount(3)
                .totalQuantity(1840.0)
                .quantityUnit("kg")
                .grossRevenue(revenue)
                .interventionCount(11)
                .totalCost(cost)
                .costByInterventionType(Map.of())
                .margin(revenue.subtract(cost))
                .plantedArea(0.8)
                .missingData(List.of())
                .limitation("Constat, pas démonstration.")
                .summary("Résumé.")
                .generatedAt(Instant.parse("2026-08-19T10:00:00Z"))
                .build();
    }
}
