package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.GrowthStage;
import com.sni.bilanga.farm.model.Crop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bornes de stade, et les trois cas limites qui décident de la justesse.
 *
 * <p>Le stade commande les seuils agronomiques retenus par
 * {@code CropRequirementResolver} : une erreur ici ne produit pas un message
 * bizarre, elle produit un diagnostic <em>faux présenté avec la même
 * assurance</em> qu'un diagnostic juste. C'est ce qui rend ces bornes dignes
 * d'un test plutôt que d'une relecture.
 */
@DisplayName("GrowthStageResolver — le stade se déduit de la plantation")
class GrowthStageResolverTest {

    private final GrowthStageResolver resolver = new GrowthStageResolver();

    private static final LocalDate PLANTED = LocalDate.of(2026, 3, 1);

    // ============================================================
    // Bornes de stade
    // ============================================================

    @Nested
    @DisplayName("Tomate — cycle de 120 jours")
    class Tomato {

        /**
         * Les fractions du cycle : 0,10 / 0,40 / 0,60 / 0,85 / 1,00.
         * Sur 120 jours, cela place les bornes à 12, 48, 72 et 102 jours.
         *
         * <p>Chaque borne est testée <em>sur</em> la valeur et juste après :
         * la comparaison est {@code progress <= endsAt}, donc le jour de la
         * borne appartient au stade qui s'achève. Une inversion en {@code <}
         * décalerait tous les stades d'un jour sans que rien ne le signale.
         */
        @ParameterizedTest(name = "J+{0} → {1}")
        @CsvSource({
                "1,   LEVEE",
                "12,  LEVEE",            // borne 0,10 exactement
                "13,  CROISSANCE",
                "48,  CROISSANCE",       // borne 0,40
                "49,  FLORAISON",
                "72,  FLORAISON",        // borne 0,60
                "73,  FRUCTIFICATION",
                "102, FRUCTIFICATION",   // borne 0,85
                "103, MATURATION",
                "120, MATURATION",       // fin du cycle
        })
        void stageAtEachBoundary(int daysElapsed, GrowthStage expected) {
            assertThat(resolver.stageFor("tomate", PLANTED, 120, PLANTED.plusDays(daysElapsed)))
                    .isEqualTo(expected);
        }

        /**
         * Un cycle déclaré plus court doit décaler toutes les bornes
         * proportionnellement — c'est tout l'intérêt d'exprimer les phases en
         * fraction plutôt qu'en jours. À 60 jours, la floraison commence à J+25
         * au lieu de J+49.
         */
        @Test
        @DisplayName("un cycle déclaré plus court avance toutes les bornes")
        void declaredCycleShiftsBoundaries() {
            assertThat(resolver.stageFor("tomate", PLANTED, 60, PLANTED.plusDays(25)))
                    .isEqualTo(GrowthStage.FLORAISON);

            assertThat(resolver.stageFor("tomate", PLANTED, 120, PLANTED.plusDays(25)))
                    .as("à 120 jours, J+25 est encore en croissance")
                    .isEqualTo(GrowthStage.CROISSANCE);
        }
    }

    @Nested
    @DisplayName("Manioc — cycle de 330 jours, et il tubérise au lieu de fructifier")
    class Cassava {

        /** Fractions 0,08 / 0,35 / 0,75 / 1,00 → 26, 115, 247 jours sur 330. */
        @ParameterizedTest(name = "J+{0} → {1}")
        @CsvSource({
                "1,   LEVEE",
                "26,  LEVEE",
                "27,  CROISSANCE",
                "115, CROISSANCE",
                "116, TUBERISATION",
                "247, TUBERISATION",
                "248, MATURATION",
                "330, MATURATION",
        })
        void stageAtEachBoundary(int daysElapsed, GrowthStage expected) {
            assertThat(resolver.stageFor("manioc", PLANTED, 330, PLANTED.plusDays(daysElapsed)))
                    .isEqualTo(expected);
        }

        /**
         * Les deux cultures n'ont pas les mêmes stades, et c'est ce qui interdit
         * une séquence commune : la tomate ne tubérise pas, le manioc ne fructifie
         * pas. Confondre les deux produirait un stade sans seuils associés dans
         * {@code crop_stage_requirement}, donc silencieusement ignoré.
         */
        @Test
        @DisplayName("le manioc ne passe jamais par FRUCTIFICATION")
        void cassavaNeverFruits() {
            List<GrowthStage> stages = resolver.stageTimeline(crop("manioc", PLANTED, 330)).stream()
                    .map(GrowthStageResolver.StageStart::stage)
                    .toList();

            assertThat(stages).doesNotContain(GrowthStage.FRUCTIFICATION);
            assertThat(stages).containsExactly(GrowthStage.LEVEE, GrowthStage.CROISSANCE,
                    GrowthStage.TUBERISATION, GrowthStage.MATURATION);
        }
    }

    // ============================================================
    // Les cas où l'on ne sait pas
    // ============================================================

    @Nested
    @DisplayName("« Je ne sais pas » se dit null, jamais « aucun stade »")
    class Unknown {

        /**
         * {@code null} signifie « je ne sais pas », et l'appelant doit alors
         * <em>conserver</em> la valeur enregistrée. Renvoyer un stade par défaut
         * écraserait une saisie manuelle correcte par une supposition.
         */
        @ParameterizedTest
        @ValueSource(strings = {"mais", "haricot", "TOMATO", "riz"})
        @DisplayName("culture inconnue de la base de connaissance → null")
        void unknownCropYieldsNull(String cropName) {
            assertThat(resolver.stageFor(cropName, PLANTED, 120, PLANTED.plusDays(30))).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("culture absente ou vide → null")
        void blankCropYieldsNull(String cropName) {
            assertThat(resolver.stageFor(cropName, PLANTED, 120, PLANTED.plusDays(30))).isNull();
        }

        @Test
        @DisplayName("date de plantation absente → null")
        void missingPlantingDateYieldsNull() {
            assertThat(resolver.stageFor("tomate", null, 120, LocalDate.now())).isNull();
        }

        @Test
        @DisplayName("culture nulle → null, sans exception")
        void nullCropYieldsNull() {
            assertThat(resolver.stageFor(null)).isNull();
        }
    }

    // ============================================================
    // Les deux extrémités du calendrier
    // ============================================================

    @Nested
    @DisplayName("Aux deux bouts du cycle")
    class Edges {

        /**
         * Une date de plantation à venir décrit une plantation <em>programmée</em>,
         * pas une erreur de saisie. Rendre le premier stade est la réponse juste ;
         * rendre {@code null} ferait perdre l'information, et rendre le dernier
         * serait absurde.
         */
        @Test
        @DisplayName("plantation à venir → le premier stade, pas une erreur")
        void futurePlantingYieldsFirstStage() {
            LocalDate future = LocalDate.now().plusDays(30);

            assertThat(resolver.stageFor("tomate", future, 120, LocalDate.now()))
                    .isEqualTo(GrowthStage.LEVEE);
        }

        @Test
        @DisplayName("le jour même de la plantation → le premier stade")
        void plantingDayYieldsFirstStage() {
            assertThat(resolver.stageFor("tomate", PLANTED, 120, PLANTED))
                    .isEqualTo(GrowthStage.LEVEE);
        }

        /**
         * Cycle dépassé : la culture est mûre, voire en retard de récolte. Le
         * dernier stade reste le plus proche de la réalité — c'est le
         * <em>statut</em> de la culture qui doit alors passer à TERMINEE, pas son
         * stade qui doit devenir indéfini.
         */
        @ParameterizedTest(name = "J+{0}")
        @ValueSource(ints = {121, 200, 3650})
        @DisplayName("cycle largement dépassé → le dernier stade, sans débordement")
        void overdueYieldsLastStage(int daysElapsed) {
            assertThat(resolver.stageFor("tomate", PLANTED, 120, PLANTED.plusDays(daysElapsed)))
                    .isEqualTo(GrowthStage.MATURATION);
        }
    }

    // ============================================================
    // Durée de cycle
    // ============================================================

    @Nested
    @DisplayName("Durée de cycle retenue")
    class CycleDuration {

        @Test
        @DisplayName("la valeur déclarée prime sur la référence de l'espèce")
        void declaredWins() {
            assertThat(resolver.cycleDays("tomate", 95)).isEqualTo(95);
        }

        @Test
        @DisplayName("à défaut, la référence de l'espèce")
        void defaultsPerSpecies() {
            assertThat(resolver.cycleDays("tomate", null)).isEqualTo(120);
            assertThat(resolver.cycleDays("manioc", null)).isEqualTo(330);
        }

        /**
         * Zéro et négatif sont écartés au même titre que {@code null} : ils
         * viennent d'une saisie fautive, et les honorer produirait une division
         * par zéro ou un progrès négatif.
         */
        @ParameterizedTest
        @ValueSource(ints = {0, -1, -365})
        @DisplayName("zéro ou négatif retombe sur la référence")
        void nonPositiveFallsBack(int declared) {
            assertThat(resolver.cycleDays("tomate", declared)).isEqualTo(120);
        }

        @Test
        @DisplayName("culture inconnue → 120 jours, faute de mieux")
        void unknownCropFallsBackTo120() {
            assertThat(resolver.cycleDays("haricot", null)).isEqualTo(120);
        }
    }

    // ============================================================
    // Chronologie reconstituée
    // ============================================================

    @Nested
    @DisplayName("stageTimeline — les dates de changement, reconstituées")
    class Timeline {

        /**
         * Les changements de stade ne sont enregistrés nulle part. Comme le stade
         * est une fonction déterministe de la date de plantation, ces dates se
         * reconstituent — et c'est ce qui permet à la chronologie de la parcelle
         * de les montrer sans qu'il ait fallu les stocker.
         */
        @Test
        @DisplayName("chaque stade commence là où le précédent s'achève")
        void startsChainFromPlanting() {
            List<GrowthStageResolver.StageStart> timeline =
                    resolver.stageTimeline(crop("tomate", PLANTED, 120));

            assertThat(timeline).hasSize(5);
            assertThat(timeline.getFirst().startsOn())
                    .as("le premier stade commence à la plantation")
                    .isEqualTo(PLANTED);

            // Le LENDEMAIN de la borne : le jour de la borne appartient au stade
            // qui s'achève, exactement comme dans stageFor (progression <= fin).
            assertThat(timeline).extracting(GrowthStageResolver.StageStart::startsOn)
                    .containsExactly(
                            PLANTED,                  // LEVEE           → J+0
                            PLANTED.plusDays(13),     // CROISSANCE      → après 0,10 (J+12)
                            PLANTED.plusDays(49),     // FLORAISON       → après 0,40 (J+48)
                            PLANTED.plusDays(73),     // FRUCTIFICATION  → après 0,60 (J+72)
                            PLANTED.plusDays(103));   // MATURATION      → après 0,85 (J+102)
        }

        /**
         * Cohérence entre les deux méthodes : le stade rendu par
         * {@code stageFor} à une date donnée doit être celui dont la fenêtre
         * contient cette date d'après {@code stageTimeline}. Les deux calculs sont
         * distincts dans le code, et rien ne garantissait qu'ils s'accordent.
         */
        @Test
        @DisplayName("stageFor et stageTimeline s'accordent sur tout le cycle")
        void bothMethodsAgree() {
            Crop crop = crop("tomate", PLANTED, 120);
            List<GrowthStageResolver.StageStart> timeline = resolver.stageTimeline(crop);

            for (int day = 0; day <= 120; day++) {
                LocalDate asOf = PLANTED.plusDays(day);
                GrowthStage computed = resolver.stageFor("tomate", PLANTED, 120, asOf);

                GrowthStage fromTimeline = timeline.stream()
                        .filter(start -> !start.startsOn().isAfter(asOf))
                        .map(GrowthStageResolver.StageStart::stage)
                        .reduce((first, second) -> second)
                        .orElse(null);

                assertThat(computed)
                        .as("J+%d : stageFor et stageTimeline doivent concorder", day)
                        .isEqualTo(fromTimeline);
            }
        }

        @Test
        @DisplayName("sans date de plantation, la chronologie est vide et non nulle")
        void emptyWithoutPlantingDate() {
            assertThat(resolver.stageTimeline(crop("tomate", null, 120))).isEmpty();
            assertThat(resolver.stageTimeline(null)).isEmpty();
            assertThat(resolver.stageTimeline(crop("haricot", PLANTED, 120))).isEmpty();
        }
    }

    // ============================================================
    // Récolte attendue
    // ============================================================

    @Nested
    @DisplayName("Récolte attendue")
    class Harvest {

        @Test
        @DisplayName("la date saisie prime sur le calcul")
        void declaredHarvestWins() {
            Crop crop = crop("tomate", PLANTED, 120);
            crop.setExpectedHarvestDate(LocalDate.of(2026, 6, 15));

            assertThat(resolver.expectedHarvestDate(crop)).isEqualTo(LocalDate.of(2026, 6, 15));
        }

        @Test
        @DisplayName("à défaut, plantation + durée de cycle")
        void computedFromCycle() {
            assertThat(resolver.expectedHarvestDate(crop("tomate", PLANTED, 120)))
                    .isEqualTo(PLANTED.plusDays(120));
        }

        @Test
        @DisplayName("sans date de plantation ni date saisie → null")
        void nullWithoutAnything() {
            assertThat(resolver.expectedHarvestDate(crop("tomate", null, 120))).isNull();
            assertThat(resolver.expectedHarvestDate(null)).isNull();
        }

        /**
         * Négatif quand le terme est passé, et c'est précisément le cas qui mérite
         * d'être vu : une récolte en retard est une information, pas une anomalie
         * à masquer par un plancher à zéro.
         */
        @Test
        @DisplayName("daysToHarvest est négatif quand le terme est dépassé")
        void negativeWhenOverdue() {
            Crop overdue = crop("tomate", LocalDate.now().minusDays(140), 120);

            assertThat(resolver.daysToHarvest(overdue))
                    .isNotNull()
                    .isNegative()
                    .isEqualTo(-20);
        }

        @Test
        @DisplayName("et positif avant le terme")
        void positiveBeforeTerm() {
            Crop young = crop("tomate", LocalDate.now().minusDays(30), 120);

            assertThat(resolver.daysToHarvest(young)).isEqualTo(90);
        }
    }

    // ============================================================
    // Péremption du stade enregistré
    // ============================================================

    @Nested
    @DisplayName("isStale — le stade enregistré est-il périmé ?")
    class Staleness {

        @Test
        @DisplayName("vrai quand le stade en base diffère du stade calculé")
        void staleWhenDifferent() {
            Crop crop = crop("tomate", LocalDate.now().minusDays(80), 120);
            crop.setGrowthStage("LEVEE");   // saisi à la plantation, jamais corrigé

            assertThat(resolver.isStale(crop))
                    .as("J+80 sur 120 jours : la culture fructifie, pas ne lève")
                    .isTrue();
        }

        @Test
        @DisplayName("un stade absent en base est toujours à renseigner")
        void staleWhenAbsent() {
            Crop crop = crop("tomate", LocalDate.now().minusDays(80), 120);
            crop.setGrowthStage(null);

            assertThat(resolver.isStale(crop)).isTrue();
        }

        @Test
        @DisplayName("faux quand ils concordent")
        void notStaleWhenAligned() {
            Crop crop = crop("tomate", LocalDate.now().minusDays(80), 120);
            crop.setGrowthStage("FRUCTIFICATION");

            assertThat(resolver.isStale(crop)).isFalse();
        }

        /**
         * Faux — et non vrai — quand le calcul n'est pas fondé. Le contraire
         * effacerait un stade correctement saisi à la main pour une culture que
         * la base de connaissance ne couvre pas.
         */
        @Test
        @DisplayName("faux quand le calcul n'est pas fondé, pour ne rien écraser")
        void notStaleWhenUncomputable() {
            Crop crop = crop("haricot", LocalDate.now().minusDays(80), 120);
            crop.setGrowthStage("FLORAISON");

            assertThat(resolver.isStale(crop)).isFalse();
        }
    }

    // ============================================================
    // Fabrique
    // ============================================================
    private static Crop crop(String cropName, LocalDate plantingDate, Integer cycleDays) {
        Crop crop = new Crop();
        crop.setCropName(cropName);
        crop.setPlantingDate(plantingDate);
        crop.setCycleDurationDays(cycleDays);
        return crop;
    }
}
