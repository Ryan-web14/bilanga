package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.farm.dto.response.CropThresholds;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.knowledge.model.CropRequirement;
import com.sni.bilanga.knowledge.service.support.CropRequirementResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Dire <strong>sur quoi</strong> le moteur juge, et d'où vient chaque seuil.
 *
 * <p>La propriété qui compte est l'origine : sans elle, un exploitant voit le système
 * changer d'avis — le même taux d'humidité déclenche un conseil en fructification et pas
 * en levée. Dire que le seuil lui-même a changé transforme une incohérence apparente en
 * information agronomique.
 */
@DisplayName("ThresholdsAssembler — les seuils effectifs, stade par stade")
class ThresholdsAssemblerTest {

    private CropRequirementResolver requirementResolver;
    private ThresholdsAssembler assembler;

    @BeforeEach
    void setUp() {
        requirementResolver = mock(CropRequirementResolver.class);
        assembler = new ThresholdsAssembler(requirementResolver, new GrowthStageResolver());
    }

    // ============================================================
    // Origine des valeurs
    // ============================================================

    @Nested
    @DisplayName("L'origine de chaque seuil")
    class Origin {

        @Test
        @DisplayName("sans surcharge, tout est GENERALE")
        void withoutOverrideEverythingIsGeneral() {
            given(baseline(), baseline(), false);

            CropThresholds thresholds = assembler.assemble(tomato());

            assertThat(thresholds.getStages()).isNotEmpty();
            assertThat(thresholds.getStages()).allSatisfy(stage -> {
                assertThat(stage.getHasStageOverride()).isFalse();
                assertThat(stage.getMeasures())
                        .extracting(CropThresholds.ThresholdRange::getOrigin)
                        .containsOnly("GENERALE");
            });
        }

        /**
         * Le cœur du lot : la V10 ne porte que les <em>écarts</em> au seuil général, une
         * colonne nulle signifiant « ce stade n'infléchit pas ce seuil ». Seules les
         * mesures effectivement modifiées doivent donc basculer.
         */
        @Test
        @DisplayName("une surcharge PARTIELLE ne fait basculer que les mesures modifiées")
        void partialOverrideOnlyFlipsChangedMeasures() {
            CropRequirement effective = baseline();
            effective.setHumSolMin(45.0);        // seul l'humidité est infléchie
            given(baseline(), effective, true);

            CropThresholds.StageThresholds stage = assembler.assemble(tomato())
                    .getStages().getFirst();

            assertThat(stage.getHasStageOverride()).isTrue();
            assertThat(range(stage, "humidite_sol").getOrigin()).isEqualTo("STADE");
            assertThat(range(stage, "humidite_sol").getMin()).isEqualTo(45.0);
            assertThat(range(stage, "temperature").getOrigin())
                    .as("la température n'a pas bougé")
                    .isEqualTo("GENERALE");
            assertThat(range(stage, "ph").getOrigin()).isEqualTo("GENERALE");
        }

        /**
         * Une ligne de surcharge qui reprend la valeur générale n'infléchit rien.
         * L'annoncer comme propre au stade ferait chercher une nuance qui n'existe pas.
         */
        @Test
        @DisplayName("une surcharge qui reprend la valeur générale reste GENERALE")
        void identicalOverrideStaysGeneral() {
            given(baseline(), baseline(), true);

            CropThresholds.StageThresholds stage = assembler.assemble(tomato())
                    .getStages().getFirst();

            assertThat(stage.getHasStageOverride())
                    .as("la ligne existe bien en base")
                    .isTrue();
            assertThat(stage.getMeasures())
                    .extracting(CropThresholds.ThresholdRange::getOrigin)
                    .as("mais aucune valeur ne diffère")
                    .containsOnly("GENERALE");
        }

        @Test
        @DisplayName("la tolérance à la sécheresse porte aussi son origine")
        void toleranceCarriesItsOrigin() {
            CropRequirement effective = baseline();
            effective.setToleranceSecheresse(0.45);
            given(baseline(), effective, true);

            CropThresholds.StageThresholds stage = assembler.assemble(tomato())
                    .getStages().getFirst();

            assertThat(stage.getToleranceSecheresse()).isEqualTo(0.45);
            assertThat(stage.getToleranceOrigin()).isEqualTo("STADE");
        }
    }

    // ============================================================
    // Ce qui est rendu, et ce qui ne l'est pas
    // ============================================================

    @Nested
    @DisplayName("Les mesures rendues")
    class Measures {

        /**
         * Un excès d'azote se lit sur le déséquilibre NPK, pas sur un plafond par
         * élément. Inventer un maximum donnerait un seuil que le moteur n'applique pas.
         */
        @Test
        @DisplayName("les seuils nutritifs n'ont pas de maximum")
        void nutrientsHaveNoCeiling() {
            given(baseline(), baseline(), false);

            CropThresholds.StageThresholds stage = assembler.assemble(tomato())
                    .getStages().getFirst();

            assertThat(range(stage, "azote").getMax()).isNull();
            assertThat(range(stage, "azote").getMin()).isEqualTo(40.0);
            assertThat(range(stage, "azote").getStatement()).contains("au moins");
        }

        /**
         * La rendre à zéro laisserait croire à un seuil, alors qu'il n'y en a pas — et un
         * pH minimum de 0 déclencherait un conseil sur toute mesure.
         */
        @Test
        @DisplayName("une mesure sans aucune borne est ABSENTE, pas à zéro")
        void unboundedMeasureIsOmitted() {
            CropRequirement partial = baseline();
            partial.setPhMin(null);
            partial.setPhMax(null);
            given(partial, partial, false);

            CropThresholds.StageThresholds stage = assembler.assemble(tomato())
                    .getStages().getFirst();

            assertThat(stage.getMeasures())
                    .extracting(CropThresholds.ThresholdRange::getMeasure)
                    .doesNotContain("ph")
                    .contains("humidite_sol");
        }

        @Test
        @DisplayName("l'énoncé est rédigé et porte l'origine")
        void statementIsWritten() {
            given(baseline(), baseline(), false);

            assertThat(range(assembler.assemble(tomato()).getStages().getFirst(), "humidite_sol")
                    .getStatement())
                    .contains("Humidité du sol")
                    .contains("%")
                    .contains("Seuil général de la culture");
        }
    }

    // ============================================================
    // Le cycle
    // ============================================================

    @Nested
    @DisplayName("Le cycle et ses réserves")
    class Cycle {

        @Test
        @DisplayName("les cinq stades de la tomate sont rendus, datés, le courant marqué")
        void allStagesAreReturned() {
            given(baseline(), baseline(), false);

            CropThresholds thresholds = assembler.assemble(tomato());

            assertThat(thresholds.getStages()).hasSize(5);
            assertThat(thresholds.getStages())
                    .extracting(CropThresholds.StageThresholds::getStage)
                    .containsExactly("LEVEE", "CROISSANCE", "FLORAISON",
                                     "FRUCTIFICATION", "MATURATION");
            assertThat(thresholds.getStages())
                    .allSatisfy(stage -> assertThat(stage.getStartsOn()).isNotNull());
            assertThat(thresholds.getStages())
                    .filteredOn(stage -> Boolean.TRUE.equals(stage.getCurrent()))
                    .as("le stade courant est marqué une seule fois")
                    .hasSize(1);
        }

        @Test
        @DisplayName("sans seuils enregistrés, missingData l'explique au lieu de rendre vide")
        void missingRequirementIsExplained() {
            when(requirementResolver.baseline(anyString())).thenReturn(Optional.empty());
            when(requirementResolver.resolve(anyString(), any())).thenReturn(Optional.empty());
            when(requirementResolver.hasStageOverride(anyString(), any())).thenReturn(false);

            CropThresholds thresholds = assembler.assemble(tomato());

            assertThat(thresholds.getMissingData())
                    .anySatisfy(message -> assertThat(message)
                            .contains("Aucun seuil n'est enregistré"));
            assertThat(thresholds.getStages())
                    .allSatisfy(stage -> assertThat(stage.getMeasures()).isEmpty());
        }

        @Test
        @DisplayName("sans date de plantation, aucun stade n'est daté et on le dit")
        void missingPlantingDateIsExplained() {
            given(baseline(), baseline(), false);

            Crop undated = tomato();
            undated.setPlantingDate(null);

            CropThresholds thresholds = assembler.assemble(undated);

            assertThat(thresholds.getStages()).isEmpty();
            assertThat(thresholds.getMissingData())
                    .anySatisfy(message -> assertThat(message)
                            .contains("pas de date de plantation"));
        }

        /**
         * Les valeurs semées par V3, V6, V7 et V10 sont indicatives — le commentaire de
         * V10 le dit. Les afficher sans le rappeler les ferait passer pour des références.
         */
        @Test
        @DisplayName("la réserve sur le caractère indicatif des seuils est toujours là")
        void limitationIsAlwaysPresent() {
            given(baseline(), baseline(), false);

            assertThat(assembler.assemble(tomato()).getLimitation())
                    .contains("INDICATIVES")
                    .contains("cache");
        }
    }

    // ============================================================
    // Fabriques
    // ============================================================

    private void given(CropRequirement base, CropRequirement effective, boolean overridden) {
        when(requirementResolver.baseline(eq("tomate"))).thenReturn(Optional.of(base));
        when(requirementResolver.resolve(eq("tomate"), any()))
                .thenReturn(Optional.of(effective));
        when(requirementResolver.hasStageOverride(eq("tomate"), any())).thenReturn(overridden);
    }

    private CropThresholds.ThresholdRange range(CropThresholds.StageThresholds stage,
                                                String measure) {
        return stage.getMeasures().stream()
                .filter(range -> measure.equals(range.getMeasure()))
                .findFirst().orElseThrow(() ->
                        new AssertionError("Mesure absente de la réponse : " + measure));
    }

    private static CropRequirement baseline() {
        return CropRequirement.builder()
                .id(1L)
                .cropName("tomate")
                .phMin(6.0).phMax(6.8)
                .humSolMin(35.0).humSolMax(70.0)
                .tempMin(18.0).tempMax(30.0)
                .azoteMin(40.0)
                .phosphoreMin(15.0)
                .potassiumMin(25.0)
                .toleranceSecheresse(0.2)
                .build();
    }

    private static Crop tomato() {
        Plot plot = new Plot();
        plot.setId(42L);
        plot.setName("Parcelle Nord");

        Crop crop = new Crop();
        crop.setId(7L);
        crop.setPlot(plot);
        crop.setCropName("tomate");
        crop.setPlantingDate(LocalDate.now().minusDays(50));
        crop.setCycleDurationDays(120);
        return crop;
    }
}
