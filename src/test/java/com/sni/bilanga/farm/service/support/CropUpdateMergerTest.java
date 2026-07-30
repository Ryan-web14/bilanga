package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.GrowthStage;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.farm.dto.request.CropRequest;
import com.sni.bilanga.farm.model.Crop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La correction du défaut le plus coûteux de ce service.
 *
 * <p>La mise à jour écrasait <strong>inconditionnellement</strong> tous les champs. Un
 * client qui n'envoyait que la variété effaçait la surface plantée, la densité, le lot
 * de semence et la date de plantation — sans erreur, sans trace.
 *
 * <p>Et la conséquence se manifestait <em>ailleurs et plus tard</em> :
 * {@code plantedArea} conditionne {@code yieldPerHectare} et {@code marginPerHectare},
 * les deux seuls chiffres comparables entre parcelles. Le bilan économique affichait
 * {@code null} des semaines après la cause, et rien ne reliait les deux.
 *
 * <p>Ces tests figent la nouvelle règle <strong>et</strong> le fait qu'effacer reste
 * possible — sans quoi on aurait troqué une perte silencieuse contre une donnée
 * indélébile.
 */
@DisplayName("CropUpdateMerger — ne plus effacer ce qu'on n'a pas demandé")
class CropUpdateMergerTest {

    private final CropUpdateMerger merger = new CropUpdateMerger();

    private static final LocalDate PLANTED = LocalDate.of(2026, 4, 21);

    // ============================================================
    // Le défaut corrigé
    // ============================================================

    @Nested
    @DisplayName("Un champ absent n'est plus touché")
    class PartialUpdate {

        /**
         * <strong>Le test qui décrit exactement l'ancien bogue.</strong> Une requête ne
         * portant que la variété effaçait tout le reste.
         */
        @Test
        @DisplayName("ne changer que la variété laisse TOUS les autres champs intacts")
        void changingOneFieldPreservesTheRest() {
            Crop crop = fullCrop();

            CropRequest request = new CropRequest();
            request.setCropName(Culture.TOMATE);
            request.setVariety("Marmande");

            merger.apply(crop, request);

            assertThat(crop.getVariety()).isEqualTo("Marmande");
            assertThat(crop.getPlantedArea())
                    .as("effacée par l'ancien code — et le bilan économique devenait "
                            + "incomparable, des semaines plus tard")
                    .isEqualTo(0.8);
            assertThat(crop.getPlantDensity()).isEqualTo(25000);
            assertThat(crop.getSeedLot()).isEqualTo("LOT-2026-A17");
            assertThat(crop.getPlantingDate()).isEqualTo(PLANTED);
            assertThat(crop.getCycleDurationDays()).isEqualTo(120);
            assertThat(crop.getGrowthStage()).isEqualTo("FRUCTIFICATION");
        }

        @Test
        @DisplayName("une requête quasi vide ne détruit rien")
        void nearlyEmptyRequestDestroysNothing() {
            Crop crop = fullCrop();
            Crop reference = fullCrop();

            CropRequest request = new CropRequest();
            request.setCropName(Culture.TOMATE);

            merger.apply(crop, request);

            assertThat(crop.getVariety()).isEqualTo(reference.getVariety());
            assertThat(crop.getSeedLot()).isEqualTo(reference.getSeedLot());
            assertThat(crop.getPlantedArea()).isEqualTo(reference.getPlantedArea());
            assertThat(crop.getPlantDensity()).isEqualTo(reference.getPlantDensity());
            assertThat(crop.getPlantingDate()).isEqualTo(reference.getPlantingDate());
            assertThat(crop.getExpectedHarvestDate()).isEqualTo(reference.getExpectedHarvestDate());
        }

        /**
         * Le cas du formulaire de Rolle : un objet complet donne exactement le même
         * résultat qu'avant la correction. C'est ce qui rend le changement sûr — il ne
         * casse que le comportement sur lequel personne ne pouvait compter.
         */
        @Test
        @DisplayName("un objet COMPLET s'applique intégralement, comme avant")
        void fullObjectStillAppliesEverything() {
            Crop crop = fullCrop();

            CropRequest request = new CropRequest();
            request.setCropName(Culture.MANIOC);
            request.setVariety("Locale");
            request.setPlantingDate(LocalDate.of(2026, 6, 1));
            request.setCycleDurationDays(330);
            request.setExpectedHarvestDate(LocalDate.of(2027, 4, 27));
            request.setPlantedArea(1.5);
            request.setPlantDensity(10000);
            request.setSeedLot("LOT-2026-B02");
            request.setGrowthStage(GrowthStage.LEVEE);

            merger.apply(crop, request);

            assertThat(crop.getCropName()).isEqualTo("manioc");
            assertThat(crop.getVariety()).isEqualTo("Locale");
            assertThat(crop.getPlantingDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(crop.getCycleDurationDays()).isEqualTo(330);
            assertThat(crop.getPlantedArea()).isEqualTo(1.5);
            assertThat(crop.getPlantDensity()).isEqualTo(10000);
            assertThat(crop.getSeedLot()).isEqualTo("LOT-2026-B02");
            assertThat(crop.getGrowthStage()).isEqualTo("LEVEE");
        }

        @Test
        @DisplayName("cycle ou requête nuls ne font rien, sans exception")
        void nullInputsAreTolerated() {
            assertThatCode(() -> merger.apply(null, new CropRequest())).doesNotThrowAnyException();
            assertThatCode(() -> merger.apply(fullCrop(), null)).doesNotThrowAnyException();
        }
    }

    // ============================================================
    // Effacer reste possible
    // ============================================================

    @Nested
    @DisplayName("Effacer, mais explicitement")
    class ExplicitClearing {

        /**
         * Sans ce mécanisme, la règle « null n'est pas touché » rendrait tout effacement
         * impossible : on aurait troqué une perte de données silencieuse contre une
         * donnée indélébile. En JSON, « absent » et « null » sont indiscernables — d'où
         * une liste explicite.
         */
        @Test
        @DisplayName("clearFields vide les champs nommés")
        void clearFieldsEmptiesNamedFields() {
            Crop crop = fullCrop();

            CropRequest request = new CropRequest();
            request.setCropName(Culture.TOMATE);
            request.setClearFields(List.of("variety", "seedLot"));

            merger.apply(crop, request);

            assertThat(crop.getVariety()).isNull();
            assertThat(crop.getSeedLot()).isNull();
            assertThat(crop.getPlantedArea())
                    .as("les champs NON listés restent intacts")
                    .isEqualTo(0.8);
        }

        /**
         * L'intention explicite l'emporte sur la valeur : on applique, puis on efface.
         * L'ordre inverse rendrait {@code clearFields} inopérant dès qu'une valeur est
         * fournie, donc contradictoire plutôt qu'utile.
         */
        @Test
        @DisplayName("un champ à la fois renseigné ET listé finit vidé")
        void clearWinsOverValue() {
            Crop crop = fullCrop();

            CropRequest request = new CropRequest();
            request.setCropName(Culture.TOMATE);
            request.setVariety("Marmande");
            request.setClearFields(List.of("variety"));

            merger.apply(crop, request);

            assertThat(crop.getVariety()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"variety", "VARIETY", "Variety", "  variety  "})
        @DisplayName("les noms sont insensibles à la casse et aux espaces")
        void fieldNamesAreLenient(String field) {
            Crop crop = fullCrop();

            CropRequest request = new CropRequest();
            request.setCropName(Culture.TOMATE);
            request.setClearFields(List.of(field));

            merger.apply(crop, request);

            assertThat(crop.getVariety()).isNull();
        }

        /**
         * <strong>Refusé, jamais ignoré.</strong> Ignorer produirait le défaut qu'on
         * vient de corriger, en sens inverse : un effacement qui n'a pas lieu et ne le
         * dit pas. L'appelant croirait la donnée supprimée et découvrirait le contraire
         * des semaines plus tard — exactement le mode de défaillance qu'on élimine.
         */
        @Test
        @DisplayName("un nom inconnu est REFUSÉ, et le message liste les champs valides")
        void unknownFieldIsRefused() {
            Crop crop = fullCrop();

            CropRequest request = new CropRequest();
            request.setCropName(Culture.TOMATE);
            request.setClearFields(List.of("surfaceQuiNexistePas"));

            assertThatThrownBy(() -> merger.apply(crop, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("surfaceQuiNexistePas")
                    .hasMessageContaining("Champs effaçables")
                    .hasMessageContaining("plantedArea");
        }

        @Test
        @DisplayName("les noms inconnus sont tous rapportés, pas seulement le premier")
        void everyUnknownFieldIsReported() {
            CropRequest request = new CropRequest();
            request.setCropName(Culture.TOMATE);
            request.setClearFields(List.of("nimportequoi", "autreChose"));

            assertThatThrownBy(() -> merger.apply(fullCrop(), request))
                    .hasMessageContaining("nimportequoi")
                    .hasMessageContaining("autreChose");
        }

        /**
         * Les champs obligatoires ou pilotés par le cycle de vie ne sont pas effaçables :
         * vider {@code status} laisserait un cycle sans état, et {@code plotId} un cycle
         * orphelin.
         */
        @ParameterizedTest
        @ValueSource(strings = {"cropName", "status", "plotId", "id", "version"})
        @DisplayName("les champs structurants ne sont pas effaçables")
        void structuralFieldsAreNotClearable(String field) {
            CropRequest request = new CropRequest();
            request.setCropName(Culture.TOMATE);
            request.setClearFields(List.of(field));

            assertThatThrownBy(() -> merger.apply(fullCrop(), request))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("une liste vide, nulle, ou contenant des blancs ne fait rien")
        void emptyOrBlankClearListIsNoOp() {
            for (List<String> fields : List.of(List.<String>of(), List.of("", "   "))) {
                Crop crop = fullCrop();
                CropRequest request = new CropRequest();
                request.setCropName(Culture.TOMATE);
                request.setClearFields(fields);

                merger.apply(crop, request);

                assertThat(crop.getVariety()).isEqualTo("Roma");
            }
        }

        @Test
        @DisplayName("les huit champs annoncés sont bien effaçables")
        void everyAdvertisedFieldIsClearable() {
            assertThat(merger.clearableFields()).containsExactlyInAnyOrder(
                    "variety", "plantingDate", "cycleDurationDays", "expectedHarvestDate",
                    "plantedArea", "plantDensity", "seedLot", "growthStage");
        }
    }

    // ============================================================
    // Fabrique
    // ============================================================
    private static Crop fullCrop() {
        Crop crop = new Crop();
        crop.setId(7L);
        crop.setCropName("tomate");
        crop.setVariety("Roma");
        crop.setPlantingDate(PLANTED);
        crop.setCycleDurationDays(120);
        crop.setExpectedHarvestDate(LocalDate.of(2026, 8, 19));
        crop.setPlantedArea(0.8);
        crop.setPlantDensity(25000);
        crop.setSeedLot("LOT-2026-A17");
        crop.setGrowthStage("FRUCTIFICATION");
        crop.setStatus("EN_COURS");
        return crop;
    }
}
