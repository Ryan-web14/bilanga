package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.PlannedOperationStatus;
import com.sni.bilanga.farm.dto.response.CropItinerary;
import com.sni.bilanga.farm.dto.response.PlannedOperationResponse;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.CropPlannedOperation;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.intervention.model.Intervention;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trois valeurs se calculent ici et nulle part ailleurs — la date résolue, le retard, le
 * rapprochement inféré. Chacune serait fausse si elle était persistée, et pour une raison
 * différente : la date dès qu'on corrige la plantation, le retard dès le lendemain, le
 * rapprochement dès qu'une intervention est saisie après coup.
 */
@DisplayName("ItineraryAssembler — l'itinéraire technique d'une campagne")
class ItineraryAssemblerTest {

    private static final LocalDate PLANTING = LocalDate.parse("2026-04-01");
    private static final LocalDate TODAY = LocalDate.parse("2026-06-15");

    private final ItineraryAssembler assembler = new ItineraryAssembler(new ItineraryMatcher());

    // ============================================================
    // Résolution des dates
    // ============================================================

    @Nested
    @DisplayName("La date retenue")
    class Resolution {

        @Test
        @DisplayName("une date ferme est reprise telle quelle")
        void firmDateIsKept() {
            CropItinerary itinerary = assemble(crop(PLANTING),
                    List.of(on(1L, "IRRIGATION", "2026-05-02")), List.of());

            assertThat(line(itinerary, 1L).getResolvedDate())
                    .isEqualTo(LocalDate.parse("2026-05-02"));
        }

        /**
         * C'est la forme qui survit au clonage : elle se reporte telle quelle sur une
         * campagne plantée un autre jour.
         */
        @Test
        @DisplayName("J+n est résolu depuis la date de plantation, à la lecture")
        void relativeDateIsResolvedFromPlanting() {
            CropItinerary itinerary = assemble(crop(PLANTING),
                    List.of(after(1L, "FERTILISATION", 30)), List.of());

            assertThat(line(itinerary, 1L).getResolvedDate())
                    .isEqualTo(LocalDate.parse("2026-05-01"));
            assertThat(line(itinerary, 1L).getPlannedOn())
                    .as("aucune date ferme n'est inventée en base")
                    .isNull();
        }

        /**
         * L'opération n'est pas escamotée : elle figure dans la liste, non datée, et
         * {@code missingData} dit pourquoi. La faire disparaître donnerait un itinéraire
         * qui paraît complet.
         */
        @Test
        @DisplayName("J+n sans date de plantation → non datée, et missingData l'explique")
        void relativeDateWithoutPlantingIsExplained() {
            CropItinerary itinerary = assemble(crop(null),
                    List.of(after(1L, "FERTILISATION", 30)), List.of());

            assertThat(itinerary.getOperationCount()).isEqualTo(1);
            assertThat(line(itinerary, 1L).getResolvedDate()).isNull();
            assertThat(itinerary.getMissingData())
                    .anySatisfy(message -> assertThat(message)
                            .contains("pas de date de plantation"));
        }

        @Test
        @DisplayName("les opérations non datables passent en fin de liste")
        void undatableGoesLast() {
            CropItinerary itinerary = assemble(crop(null),
                    List.of(after(1L, "FERTILISATION", 30), on(2L, "IRRIGATION", "2026-05-02")),
                    List.of());

            assertThat(itinerary.getOperations())
                    .extracting(PlannedOperationResponse::getId)
                    .containsExactly(2L, 1L);
        }

        @Test
        @DisplayName("la date ferme prime sur J+n quand les deux sont saisies")
        void firmDateWinsOverRelative() {
            CropPlannedOperation both = on(1L, "IRRIGATION", "2026-05-20");
            both.setDaysAfterPlanting(30);

            assertThat(line(assemble(crop(PLANTING), List.of(both), List.of()), 1L)
                    .getResolvedDate())
                    .isEqualTo(LocalDate.parse("2026-05-20"));
        }
    }

    // ============================================================
    // Le retard
    // ============================================================

    @Nested
    @DisplayName("Le retard")
    class Lateness {

        @Test
        @DisplayName("date passée et rien de rapproché → en retard, avec le nombre de jours")
        void pastAndUnmatchedIsLate() {
            PlannedOperationResponse line = line(assemble(crop(PLANTING),
                    List.of(on(1L, "IRRIGATION", "2026-06-01")), List.of()), 1L);

            assertThat(line.getLate()).isTrue();
            assertThat(line.getLateByDays()).isEqualTo(14);
            assertThat(line.getMatchStatement())
                    .contains("date prévue est dépassée")
                    .contains("n'a pas été saisie");
        }

        @Test
        @DisplayName("date à venir → pas en retard")
        void futureIsNotLate() {
            assertThat(line(assemble(crop(PLANTING),
                    List.of(on(1L, "IRRIGATION", "2026-07-01")), List.of()), 1L).getLate())
                    .isFalse();
        }

        @Test
        @DisplayName("une opération rapprochée n'est jamais en retard")
        void matchedIsNeverLate() {
            PlannedOperationResponse line = line(assemble(crop(PLANTING),
                    List.of(on(1L, "IRRIGATION", "2026-06-01")),
                    List.of(done(10L, "IRRIGATION", "2026-06-02"))), 1L);

            assertThat(line.getLate()).isNull();
            assertThat(line.getInterventionId()).isEqualTo(10L);
        }

        /**
         * {@code false} laisserait croire qu'elle a été traitée à temps ; la question ne
         * se pose simplement pas.
         */
        @Test
        @DisplayName("une opération ABANDONNEE n'est ni en retard ni « à l'heure »")
        void abandonedIsNeitherLateNorOnTime() {
            CropPlannedOperation abandoned = on(1L, "IRRIGATION", "2026-06-01");
            abandoned.setStatus(PlannedOperationStatus.ABANDONNEE.name());

            PlannedOperationResponse line = line(
                    assemble(crop(PLANTING), List.of(abandoned), List.of()), 1L);

            assertThat(line.getLate()).isNull();
            assertThat(line.getLateByDays()).isNull();
        }

        @Test
        @DisplayName("une opération non datable ne peut pas être en retard")
        void undatableCannotBeLate() {
            assertThat(line(assemble(crop(null),
                    List.of(after(1L, "IRRIGATION", 30)), List.of()), 1L).getLate())
                    .isNull();
        }
    }

    // ============================================================
    // Rapprochement
    // ============================================================

    @Nested
    @DisplayName("Rapprochement inféré et rapprochement confirmé")
    class Matching {

        /**
         * La distinction que le frontend doit rendre visible : l'un est un fait, l'autre
         * une hypothèse recalculée à chaque appel.
         */
        @Test
        @DisplayName("un rapprochement inféré se dit comme tel")
        void inferredSaysSo() {
            PlannedOperationResponse line = line(assemble(crop(PLANTING),
                    List.of(on(1L, "FERTILISATION", "2026-05-01")),
                    List.of(done(10L, "FERTILISATION", "2026-05-03"))), 1L);

            assertThat(line.getMatchConfirmed()).isFalse();
            assertThat(line.getMatchConfidence()).isEqualTo("EXACTE");
            assertThat(line.getMatchGapDays()).isEqualTo(2);
            assertThat(line.getMatchStatement())
                    .contains("Inféré par le système, non confirmé");
        }

        @Test
        @DisplayName("un rapprochement confirmé n'est pas recalculé")
        void confirmedIsNotRecomputed() {
            Intervention far = done(10L, "FERTILISATION", "2026-09-30");   // hors tolérance
            CropPlannedOperation operation = on(1L, "FERTILISATION", "2026-05-01");
            operation.setIntervention(far);
            operation.setMatchConfidence("MANUELLE");
            operation.setStatus(PlannedOperationStatus.REALISEE.name());

            PlannedOperationResponse line = line(
                    assemble(crop(PLANTING), List.of(operation), List.of(far)), 1L);

            assertThat(line.getMatchConfirmed())
                    .as("une confirmation humaine ne se défait pas toute seule")
                    .isTrue();
            assertThat(line.getInterventionId()).isEqualTo(10L);
            assertThat(line.getMatchStatement()).contains("confirmé manuellement");
        }

        /**
         * Sans cela, une intervention confirmée sur une opération serait réutilisée par
         * l'inférence sur une autre — et comptée deux fois dans le coût constaté.
         */
        @Test
        @DisplayName("une intervention confirmée n'est plus disponible pour l'inférence")
        void confirmedInterventionIsLockedOut() {
            Intervention shared = done(10L, "TRAITEMENT", "2026-05-02");

            CropPlannedOperation confirmed = on(1L, "TRAITEMENT", "2026-05-01");
            confirmed.setIntervention(shared);
            confirmed.setMatchConfidence("MANUELLE");

            CropPlannedOperation open = on(2L, "TRAITEMENT", "2026-05-02");

            CropItinerary itinerary = assemble(crop(PLANTING),
                    List.of(confirmed, open), List.of(shared));

            assertThat(line(itinerary, 1L).getInterventionId()).isEqualTo(10L);
            assertThat(line(itinerary, 2L).getInterventionId()).isNull();
            assertThat(itinerary.getMatchedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("sans rapprochement, l'énoncé le dit plutôt que de rester vide")
        void absenceIsStated() {
            assertThat(line(assemble(crop(PLANTING),
                    List.of(on(1L, "IRRIGATION", "2026-07-01")), List.of()), 1L)
                    .getMatchStatement())
                    .isEqualTo("Aucune intervention rapprochée pour l'instant.");
        }
    }

    // ============================================================
    // Économie prévisionnelle
    // ============================================================

    @Nested
    @DisplayName("Coût prévu, coût constaté")
    class Costs {

        @Test
        @DisplayName("le dépassement est calculé et formulé")
        void overrunIsStated() {
            CropPlannedOperation operation = on(1L, "FERTILISATION", "2026-05-01");
            operation.setEstimatedCost(new BigDecimal("15000.00"));

            Intervention intervention = done(10L, "FERTILISATION", "2026-05-02");
            intervention.setCost(new BigDecimal("18000.00"));

            CropItinerary itinerary = assemble(crop(PLANTING),
                    List.of(operation), List.of(intervention));

            assertThat(itinerary.getTotalEstimatedCost()).isEqualByComparingTo("15000.00");
            assertThat(itinerary.getTotalActualCost()).isEqualByComparingTo("18000.00");
            assertThat(itinerary.getCostVariance()).isEqualByComparingTo("3000.00");
            assertThat(itinerary.getSummary()).contains("dépassement");
        }

        /**
         * Un écart calculé contre zéro ferait passer une absence de saisie pour une
         * économie — la lecture la plus flatteuse, et la plus fausse.
         */
        @Test
        @DisplayName("sans coût constaté, l'écart est null et non une économie")
        void missingActualCostIsNotAnEconomy() {
            CropPlannedOperation operation = on(1L, "FERTILISATION", "2026-05-01");
            operation.setEstimatedCost(new BigDecimal("15000.00"));

            CropItinerary itinerary = assemble(crop(PLANTING), List.of(operation), List.of());

            assertThat(itinerary.getTotalActualCost()).isNull();
            assertThat(itinerary.getCostVariance()).isNull();
            assertThat(itinerary.getSummary()).doesNotContain("économie");
        }

        @Test
        @DisplayName("aucun coût estimé → missingData le dit")
        void noEstimatedCostIsReported() {
            CropItinerary itinerary = assemble(crop(PLANTING),
                    List.of(on(1L, "IRRIGATION", "2026-05-01")), List.of());

            assertThat(itinerary.getTotalEstimatedCost()).isNull();
            assertThat(itinerary.getMissingData())
                    .anySatisfy(message -> assertThat(message).contains("coût estimé"));
        }
    }

    // ============================================================
    // Synthèse
    // ============================================================

    @Nested
    @DisplayName("La synthèse")
    class Summary {

        /**
         * {@code 0 %} laisserait croire que rien n'a été fait, alors que rien n'a été
         * planifié. Ce sont deux situations opposées.
         */
        @Test
        @DisplayName("un itinéraire vide n'a pas de taux de réalisation")
        void emptyItineraryHasNoCompletionRate() {
            CropItinerary itinerary = assemble(crop(PLANTING), List.of(), List.of());

            assertThat(itinerary.getOperationCount()).isZero();
            assertThat(itinerary.getCompletionRate()).isNull();
            assertThat(itinerary.getSummary()).contains("Aucune opération planifiée");
            assertThat(itinerary.getLimitation()).isNotBlank();
        }

        @Test
        @DisplayName("le taux de réalisation compte les rapprochements")
        void completionRateCountsMatches() {
            CropItinerary itinerary = assemble(crop(PLANTING),
                    List.of(on(1L, "IRRIGATION", "2026-05-01"),
                            on(2L, "FERTILISATION", "2026-05-10")),
                    List.of(done(10L, "IRRIGATION", "2026-05-01")));

            assertThat(itinerary.getCompletionRate()).isEqualTo(50.0);
            assertThat(itinerary.getMatchedCount()).isEqualTo(1);
            assertThat(itinerary.getLateCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("la réserve est toujours renseignée")
        void limitationIsAlwaysPresent() {
            assertThat(assemble(crop(PLANTING),
                    List.of(on(1L, "IRRIGATION", "2026-05-01")), List.of()).getLimitation())
                    .contains("hypothèses")
                    .contains("recalculés");
        }

        @Test
        @DisplayName("un crop nul ne fait pas échouer l'assemblage")
        void nullCropIsTolerated() {
            CropItinerary itinerary = assembler.assemble(null, null, null, TODAY);

            assertThat(itinerary.getOperationCount()).isZero();
            assertThat(itinerary.getCropId()).isNull();
            assertThat(itinerary.getSummary()).isNotBlank();
        }
    }

    @Test
    @DisplayName("le dosage est formaté une fois, côté serveur")
    void dosageIsFormattedServerSide() {
        CropPlannedOperation operation = on(1L, "FERTILISATION", "2026-05-01");
        operation.setDose(12.5);
        operation.setUnit("kg/ha");

        assertThat(line(assemble(crop(PLANTING), List.of(operation), List.of()), 1L).getDosage())
                .isEqualTo("12,50 kg/ha");
    }

    // ============================================================
    // Fabriques
    // ============================================================

    private CropItinerary assemble(Crop crop, List<CropPlannedOperation> operations,
                                   List<Intervention> interventions) {

        List<CropPlannedOperation> attached = new ArrayList<>(operations);
        attached.forEach(operation -> operation.setCrop(crop));
        return assembler.assemble(crop, attached, interventions, TODAY);
    }

    private PlannedOperationResponse line(CropItinerary itinerary, Long id) {
        return itinerary.getOperations().stream()
                .filter(operation -> id.equals(operation.getId()))
                .findFirst().orElseThrow();
    }

    private static Crop crop(LocalDate plantingDate) {
        Plot plot = new Plot();
        plot.setId(42L);
        plot.setName("Parcelle Nord");

        Crop crop = new Crop();
        crop.setId(7L);
        crop.setPlot(plot);
        crop.setCropName("tomate");
        crop.setPlantingDate(plantingDate);
        return crop;
    }

    private static CropPlannedOperation on(Long id, String type, String date) {
        return CropPlannedOperation.builder()
                .id(id)
                .type(type)
                .plannedOn(LocalDate.parse(date))
                .status(PlannedOperationStatus.PREVUE.name())
                .build();
    }

    private static CropPlannedOperation after(Long id, String type, int days) {
        return CropPlannedOperation.builder()
                .id(id)
                .type(type)
                .daysAfterPlanting(days)
                .status(PlannedOperationStatus.PREVUE.name())
                .build();
    }

    private static Intervention done(Long id, String type, String date) {
        Intervention intervention = new Intervention();
        intervention.setId(id);
        intervention.setType(type);
        intervention.setPerformedAt(LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant());
        return intervention;
    }
}
