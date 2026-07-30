package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.CropClosureReason;
import com.sni.bilanga.enums.CropStatus;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.farm.model.Crop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les quatre refus, et le repli de date.
 *
 * <p>Le refus qui compte le plus est celui de la <strong>seconde clôture</strong> :
 * sans lui, réécrire le bilan figé en ferait un total mis en cache — exactement ce que
 * le contrat de {@code MarginCalculator} interdit, et ce que la clôture prétend
 * respecter.
 */
@DisplayName("CropClosureValidator — ce qu'une clôture doit satisfaire")
class CropClosureValidatorTest {

    private final CropClosureValidator validator = new CropClosureValidator();

    private static final LocalDate PLANTED = LocalDate.now().minusDays(120);

    // ============================================================
    // Le cas nominal
    // ============================================================

    @Test
    @DisplayName("une campagne en cours, un motif, une date cohérente → accepté")
    void nominalCaseIsAccepted() {
        assertThatCode(() -> validator.validate(
                openCrop(), LocalDate.now().minusDays(2), CropClosureReason.RECOLTE_NORMALE))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(CropClosureReason.class)
    @DisplayName("tous les motifs du vocabulaire sont acceptés")
    void everyReasonIsAccepted(CropClosureReason reason) {
        assertThatCode(() -> validator.validate(openCrop(), LocalDate.now(), reason))
                .doesNotThrowAnyException();
    }

    // ============================================================
    // Les quatre refus
    // ============================================================

    @Nested
    @DisplayName("Les refus")
    class Refusals {

        /**
         * <strong>Le refus le plus important.</strong> Une seconde clôture écraserait
         * {@code closedAt}, le motif et surtout le <em>bilan figé</em>. Tout l'intérêt
         * d'un instantané est qu'il ne bouge plus : le réécrire en ferait un total mis en
         * cache, c'est-à-dire précisément ce que le contrat de {@code MarginCalculator}
         * interdit — et que la clôture prétend respecter.
         */
        @Test
        @DisplayName("une campagne DÉJÀ terminée ne se referme pas")
        void alreadyClosedIsRefused() {
            Crop closed = openCrop();
            closed.setStatus(CropStatus.TERMINEE.name());

            assertThatThrownBy(() -> validator.validate(
                    closed, LocalDate.now(), CropClosureReason.RECOLTE_NORMALE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("déjà terminée")
                    .hasMessageContaining("ne se rejoue pas");
        }

        @Test
        @DisplayName("la casse du statut est indifférente")
        void statusComparisonIsCaseInsensitive() {
            Crop closed = openCrop();
            closed.setStatus("terminee");

            assertThatThrownBy(() -> validator.validate(
                    closed, LocalDate.now(), CropClosureReason.RECOLTE_NORMALE))
                    .isInstanceOf(BusinessRuleException.class);
        }

        /**
         * Le motif est ce qui rend l'historique interprétable. Sans lui, un rendement nul
         * après une récolte normale — problème agronomique à chercher — se confond avec un
         * rendement nul après une perte climatique, qui ne signale que la météo.
         */
        @Test
        @DisplayName("le motif est obligatoire, et le message dit pourquoi")
        void missingReasonIsRefused() {
            assertThatThrownBy(() -> validator.validate(openCrop(), LocalDate.now(), null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("motif de clôture est obligatoire")
                    .hasMessageContaining("ne se distingue pas d'une perte");
        }

        /**
         * Insidieux : une date future passerait les contrôles de base, puis ferait
         * apparaître la campagne comme close alors qu'elle pousse encore — et le bilan
         * figé compterait des récoltes qui n'ont pas eu lieu.
         */
        @Test
        @DisplayName("une date de fin dans le futur est refusée")
        void futureEndDateIsRefused() {
            assertThatThrownBy(() -> validator.validate(
                    openCrop(), LocalDate.now().plusDays(1), CropClosureReason.RECOLTE_NORMALE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("pas être dans le futur");
        }

        @Test
        @DisplayName("aujourd'hui est accepté — c'est la borne, pas au-delà")
        void todayIsAccepted() {
            assertThatCode(() -> validator.validate(
                    openCrop(), LocalDate.now(), CropClosureReason.RECOLTE_NORMALE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("une date de fin antérieure à la plantation est refusée")
        void endBeforePlantingIsRefused() {
            assertThatThrownBy(() -> validator.validate(
                    openCrop(), PLANTED.minusDays(1), CropClosureReason.RECOLTE_NORMALE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("précède la date de plantation");
        }

        @Test
        @DisplayName("le jour même de la plantation est accepté — une campagne peut échouer d'emblée")
        void endOnPlantingDayIsAccepted() {
            assertThatCode(() -> validator.validate(
                    openCrop(), PLANTED, CropClosureReason.ERREUR_DE_SAISIE))
                    .doesNotThrowAnyException();
        }

        /**
         * Sans date de plantation, on ne peut rien vérifier — mais on ne refuse pas :
         * la campagne existe, et l'empêcher de se clore pour une donnée manquante à la
         * création serait la laisser ouverte indéfiniment.
         */
        @Test
        @DisplayName("sans date de plantation, la cohérence n'est pas vérifiable et n'est pas exigée")
        void missingPlantingDateDoesNotBlock() {
            Crop crop = openCrop();
            crop.setPlantingDate(null);

            assertThatCode(() -> validator.validate(
                    crop, LocalDate.now().minusYears(3), CropClosureReason.ABANDON))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("une date de fin absente ne déclenche aucun contrôle de cohérence")
        void nullEndDateSkipsDateChecks() {
            assertThatCode(() -> validator.validate(
                    openCrop(), null, CropClosureReason.RECOLTE_NORMALE))
                    .doesNotThrowAnyException();
        }
    }

    // ============================================================
    // Le repli de date
    // ============================================================

    @Nested
    @DisplayName("Date de fin effective")
    class EffectiveEndDate {

        @Test
        @DisplayName("la date demandée est retenue telle quelle")
        void requestedDateWins() {
            LocalDate requested = LocalDate.now().minusDays(5);

            assertThat(validator.effectiveEndDate(requested)).isEqualTo(requested);
        }

        /**
         * <strong>Le repli est aujourd'hui, jamais {@code expectedHarvestDate}.</strong>
         * Celle-ci est un objectif calculé par {@code GrowthStageResolver} : l'employer
         * comme constat enregistrerait une prévision en la présentant comme un fait. Le
         * jour de saisie est approximatif mais vrai ; la date prévue serait précise et
         * fausse.
         */
        @Test
        @DisplayName("à défaut, le jour de la saisie — et non la date de récolte prévue")
        void fallsBackToTodayNotToExpectedHarvest() {
            assertThat(validator.effectiveEndDate(null)).isEqualTo(LocalDate.now());
        }
    }

    // ============================================================
    // Le vocabulaire des motifs
    // ============================================================

    @Nested
    @DisplayName("Le vocabulaire porte du sens, pas seulement des libellés")
    class ReasonSemantics {

        /**
         * Sert à distinguer « rendement nul parce que rien n'a poussé » de « rendement nul
         * parce que la campagne n'a jamais été menée à terme ». Sans cette distinction,
         * une moyenne de rendements mêlerait des campagnes et des accidents.
         */
        @Test
        @DisplayName("harvested sépare les campagnes qui ont produit des autres")
        void harvestedSeparatesProducingCampaigns() {
            assertThat(CropClosureReason.RECOLTE_NORMALE.isHarvested()).isTrue();
            assertThat(CropClosureReason.RECOLTE_ANTICIPEE.isHarvested()).isTrue();

            assertThat(CropClosureReason.PERTE_MALADIE.isHarvested()).isFalse();
            assertThat(CropClosureReason.ABANDON.isHarvested()).isFalse();
            assertThat(CropClosureReason.RETOURNEE.isHarvested()).isFalse();
        }

        @Test
        @DisplayName("isLoss ne retient que les trois pertes subies")
        void lossCoversTheThreeAccidents() {
            assertThat(CropClosureReason.PERTE_MALADIE.isLoss()).isTrue();
            assertThat(CropClosureReason.PERTE_CLIMATIQUE.isLoss()).isTrue();
            assertThat(CropClosureReason.PERTE_RAVAGEURS.isLoss()).isTrue();

            assertThat(CropClosureReason.ABANDON.isLoss())
                    .as("un abandon est une décision, pas un accident")
                    .isFalse();
            assertThat(CropClosureReason.RETOURNEE.isLoss()).isFalse();
        }

        /**
         * Seule l'erreur de saisie sort de l'historique. Un abandon a bien occupé le sol
         * et consommé des intrants : il doit rester dans la succession, sans quoi le
         * précédent cultural serait faux.
         */
        @Test
        @DisplayName("seule ERREUR_DE_SAISIE est écartée de l'historique")
        void onlyDataEntryErrorIsExcluded() {
            assertThat(CropClosureReason.ERREUR_DE_SAISIE.isExcludedFromHistory()).isTrue();

            for (CropClosureReason reason : CropClosureReason.values()) {
                if (reason != CropClosureReason.ERREUR_DE_SAISIE) {
                    assertThat(reason.isExcludedFromHistory())
                            .as("%s a réellement occupé le sol", reason)
                            .isFalse();
                }
            }
        }

        @Test
        @DisplayName("le vocabulaire est tolérant à la casse en entrée")
        void parsingIsCaseInsensitive() {
            assertThat(CropClosureReason.from("recolte_normale"))
                    .isEqualTo(CropClosureReason.RECOLTE_NORMALE);
            assertThat(CropClosureReason.from("n'importe quoi")).isNull();
            assertThat(CropClosureReason.from(null)).isNull();
        }
    }

    // ============================================================
    // Fabrique
    // ============================================================
    private static Crop openCrop() {
        Crop crop = new Crop();
        crop.setId(7L);
        crop.setCropName("tomate");
        crop.setPlantingDate(PLANTED);
        crop.setStatus(CropStatus.EN_COURS.name());
        return crop;
    }
}
