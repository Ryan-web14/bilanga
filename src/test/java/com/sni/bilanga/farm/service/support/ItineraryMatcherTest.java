package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.MatchConfidence;
import com.sni.bilanga.farm.service.support.ItineraryMatcher.ActualRef;
import com.sni.bilanga.farm.service.support.ItineraryMatcher.Match;
import com.sni.bilanga.farm.service.support.ItineraryMatcher.PlannedRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rapprocher le prévu du réalisé — une inférence, et qui doit le rester.
 *
 * <p>La propriété qui compte est <strong>un pour un</strong> : deux opérations du même
 * type ne peuvent pas se réclamer de la même intervention. C'est exactement ce qu'un
 * appariement naïf — « pour chaque opération, l'intervention la plus proche » — produit,
 * et le défaut est invisible : la liste paraît complète, une opération est simplement
 * comptée deux fois.
 */
@DisplayName("ItineraryMatcher — prévu ↔ réalisé")
class ItineraryMatcherTest {

    private final ItineraryMatcher matcher = new ItineraryMatcher();

    @Nested
    @DisplayName("Un pour un")
    class OneToOne {

        /**
         * Le cas qui justifie l'algorithme glouton. Un appariement naïf donnerait
         * l'intervention du 2 mai aux <em>deux</em> opérations, et laisserait celle du
         * 10 mai orpheline — la campagne paraîtrait avoir tout réalisé alors qu'une
         * opération manque.
         */
        @Test
        @DisplayName("deux opérations du même type reçoivent chacune la leur")
        void twoOperationsGetOneEach() {
            List<Match> matches = matcher.match(
                    List.of(planned(1L, "FERTILISATION", "2026-05-01"),
                            planned(2L, "FERTILISATION", "2026-05-03")),
                    List.of(actual(10L, "FERTILISATION", "2026-05-02"),
                            actual(20L, "FERTILISATION", "2026-05-10")));

            assertThat(matches).hasSize(2);
            assertThat(matches).extracting(Match::operationId).containsExactlyInAnyOrder(1L, 2L);
            assertThat(matches).extracting(Match::interventionId)
                    .as("aucune intervention n'est comptée deux fois")
                    .containsExactlyInAnyOrder(10L, 20L);
        }

        @Test
        @DisplayName("la paire la plus serrée est arrêtée d'abord")
        void tightestPairWinsFirst() {
            List<Match> matches = matcher.match(
                    List.of(planned(1L, "IRRIGATION", "2026-05-01"),
                            planned(2L, "IRRIGATION", "2026-05-09")),
                    List.of(actual(10L, "IRRIGATION", "2026-05-09")));

            assertThat(matches).hasSize(1);
            assertThat(matches.getFirst().operationId())
                    .as("l'opération du 9 mai est à zéro jour de l'intervention")
                    .isEqualTo(2L);
        }

        @Test
        @DisplayName("plus d'interventions que d'opérations : le surplus reste libre")
        void surplusInterventionsAreLeftOut() {
            List<Match> matches = matcher.match(
                    List.of(planned(1L, "TRAITEMENT", "2026-05-01")),
                    List.of(actual(10L, "TRAITEMENT", "2026-05-01"),
                            actual(20L, "TRAITEMENT", "2026-05-02")));

            assertThat(matches).hasSize(1);
            assertThat(matches.getFirst().interventionId()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("Les fenêtres de tolérance")
    class Tolerance {

        @Test
        @DisplayName("à deux jours près, le rapprochement est EXACTE")
        void twoDaysIsExact() {
            assertThat(single("2026-05-01", "2026-05-03").confidence())
                    .isEqualTo(MatchConfidence.EXACTE);
        }

        @Test
        @DisplayName("au-delà de deux jours et jusqu'à dix, il est PROBABLE")
        void tenDaysIsProbable() {
            assertThat(single("2026-05-01", "2026-05-04").confidence())
                    .isEqualTo(MatchConfidence.PROBABLE);
            assertThat(single("2026-05-01", "2026-05-11").confidence())
                    .isEqualTo(MatchConfidence.PROBABLE);
        }

        /**
         * Hors tolérance, il n'y a pas de rapprochement <em>douteux</em> : il n'y a pas
         * de rapprochement. Les confondre reviendrait à relier une irrigation de mars à
         * une opération prévue en août.
         */
        @Test
        @DisplayName("au-delà de dix jours, aucun rapprochement — pas un rapprochement faible")
        void beyondToleranceIsNoMatch() {
            assertThat(matcher.match(
                    List.of(planned(1L, "IRRIGATION", "2026-05-01")),
                    List.of(actual(10L, "IRRIGATION", "2026-05-12"))))
                    .isEmpty();
        }

        @Test
        @DisplayName("la tolérance vaut dans les deux sens")
        void toleranceIsSymmetric() {
            assertThat(single("2026-05-11", "2026-05-01").confidence())
                    .isEqualTo(MatchConfidence.PROBABLE);
        }
    }

    @Nested
    @DisplayName("L'écart est signé")
    class SignedGap {

        /**
         * « Systématiquement en retard » et « systématiquement en avance » ne se lisent
         * pas de la même façon. Une valeur absolue effacerait la distinction.
         */
        @Test
        @DisplayName("négatif si l'opération a été faite en avance")
        void earlyIsNegative() {
            assertThat(single("2026-05-10", "2026-05-08").gapDays()).isEqualTo(-2);
            assertThat(single("2026-05-10", "2026-05-12").gapDays()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Ce qui n'est jamais rapproché")
    class NeverMatched {

        @Test
        @DisplayName("des types différents ne se rapprochent pas, même le même jour")
        void differentTypesNeverMatch() {
            assertThat(matcher.match(
                    List.of(planned(1L, "IRRIGATION", "2026-05-01")),
                    List.of(actual(10L, "FERTILISATION", "2026-05-01"))))
                    .isEmpty();
        }

        @Test
        @DisplayName("le type est comparé sans tenir compte de la casse")
        void typeComparisonIsCaseInsensitive() {
            assertThat(matcher.match(
                    List.of(planned(1L, "irrigation", "2026-05-01")),
                    List.of(actual(10L, "IRRIGATION", "2026-05-01"))))
                    .hasSize(1);
        }

        /**
         * Une opération en {@code J+n} sur une campagne sans date de plantation n'a pas
         * de date résolue. La rapprocher au hasard serait pire que ne rien dire.
         */
        @Test
        @DisplayName("une opération sans date résolue n'est pas rapprochée")
        void undatedOperationIsSkipped() {
            assertThat(matcher.match(
                    List.of(new PlannedRef(1L, "IRRIGATION", null)),
                    List.of(actual(10L, "IRRIGATION", "2026-05-01"))))
                    .isEmpty();
        }

        @Test
        @DisplayName("les entrées dégénérées ne font pas échouer le calcul")
        void degenerateInputsAreTolerated() {
            assertThat(matcher.match(null, null)).isEmpty();
            assertThat(matcher.match(List.of(), List.of(actual(10L, "IRRIGATION", "2026-05-01"))))
                    .isEmpty();
            assertThat(matcher.match(List.of(planned(1L, "IRRIGATION", "2026-05-01")), List.of()))
                    .isEmpty();
            assertThat(matcher.match(
                    List.of(new PlannedRef(1L, null, LocalDate.parse("2026-05-01"))),
                    List.of(new ActualRef(10L, "IRRIGATION", null))))
                    .isEmpty();
        }
    }

    /**
     * Un appariement qui change d'un affichage à l'autre serait illisible — et il sera
     * affiché.
     */
    @Test
    @DisplayName("le résultat est déterministe à données égales")
    void resultIsDeterministic() {
        List<PlannedRef> planned = List.of(
                planned(1L, "DESHERBAGE", "2026-05-01"),
                planned(2L, "DESHERBAGE", "2026-05-01"));
        List<ActualRef> actual = List.of(
                actual(10L, "DESHERBAGE", "2026-05-01"),
                actual(20L, "DESHERBAGE", "2026-05-01"));

        List<Match> first = matcher.match(planned, actual);
        List<Match> second = matcher.match(planned, actual);

        assertThat(first).hasSize(2).isEqualTo(second);
    }

    // ============================================================
    // Fabriques
    // ============================================================

    private Match single(String plannedOn, String performedOn) {
        List<Match> matches = matcher.match(
                List.of(planned(1L, "IRRIGATION", plannedOn)),
                List.of(actual(10L, "IRRIGATION", performedOn)));

        assertThat(matches).as("un rapprochement était attendu").hasSize(1);
        return matches.getFirst();
    }

    private static PlannedRef planned(Long id, String type, String date) {
        return new PlannedRef(id, type, LocalDate.parse(date));
    }

    private static ActualRef actual(Long id, String type, String date) {
        return new ActualRef(id, type, LocalDate.parse(date));
    }
}
