package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.InterventionType;
import com.sni.bilanga.enums.MatchConfidence;
import com.sni.bilanga.enums.PlannedOperationStatus;
import com.sni.bilanga.farm.dto.response.CropItinerary;
import com.sni.bilanga.farm.dto.response.PlannedOperationResponse;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.CropPlannedOperation;
import com.sni.bilanga.intervention.model.Intervention;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Compose l'itinéraire technique : les opérations prévues, leur rapprochement, et ce
 * que l'ensemble dit de la campagne.
 *
 * <h2>Trois valeurs calculées ici et nulle part ailleurs</h2>
 *
 * <ul>
 *   <li><strong>La date résolue.</strong> Une opération datée en {@code J+n} n'a pas de
 *       date en base. La calculer à l'écriture la rendrait fausse dès qu'on corrige la
 *       date de plantation — et c'est justement la correction la plus fréquente.</li>
 *   <li><strong>Le retard.</strong> Le projet n'a ni ordonnanceur ni tâche de fond : un
 *       statut {@code EN_RETARD} persisté serait faux dès le lendemain.</li>
 *   <li><strong>Le rapprochement automatique.</strong> Voir {@link ItineraryMatcher} —
 *       un mauvais appariement qui se recalcule disparaît, celui qui s'écrit doit être
 *       défait à la main.</li>
 * </ul>
 *
 * <p>Sans état ni transaction.
 */
@Component
public class ItineraryAssembler {

    private static final Locale FR = Locale.FRANCE;

    private static final String LIMITATION =
            "Les rapprochements marqués « inférés » sont des hypothèses : rien, dans les "
            + "données, n'établit qu'une intervention du 14 mai est celle qui était prévue "
            + "le 12. Ils sont recalculés à chaque consultation et peuvent donc changer si "
            + "une date est corrigée ou une intervention saisie après coup. Seuls les "
            + "rapprochements confirmés à la main sont des faits.";

    private final ItineraryMatcher matcher;

    public ItineraryAssembler(ItineraryMatcher matcher) {
        this.matcher = matcher;
    }

    /**
     * @param operations    itinéraire de la campagne
     * @param interventions interventions rattachées à la même campagne
     * @param today         jour de référence, injecté pour que le retard soit testable
     */
    public CropItinerary assemble(Crop crop,
                                  List<CropPlannedOperation> operations,
                                  List<Intervention> interventions,
                                  LocalDate today) {

        List<CropPlannedOperation> retained = operations == null ? List.of() : operations;
        List<Intervention> actual = interventions == null ? List.of() : interventions;
        List<String> missing = new ArrayList<>();

        LocalDate plantingDate = crop == null ? null : crop.getPlantingDate();

        // ------------------------------------------------------------
        // 1. Résoudre les dates. Une opération en J+n sur une campagne sans date de
        //    plantation reste non datable — et on le dit, plutôt que de la faire
        //    disparaître de la séquence sans explication.
        // ------------------------------------------------------------
        Map<Long, LocalDate> resolved = new LinkedHashMap<>();
        boolean undatable = false;

        for (CropPlannedOperation operation : retained) {
            LocalDate date = resolveDate(operation, plantingDate);
            resolved.put(operation.getId(), date);
            undatable |= date == null;
        }

        if (undatable && plantingDate == null) {
            missing.add("Des opérations sont datées en jours après plantation, mais la "
                    + "campagne n'a pas de date de plantation : elles ne peuvent pas être "
                    + "placées dans le calendrier. Renseignez la date de plantation.");
        }

        // ------------------------------------------------------------
        // 2. Les rapprochements CONFIRMÉS sont acquis : ils ne se recalculent pas, et
        //    leurs deux membres sortent du jeu de l'inférence.
        // ------------------------------------------------------------
        Map<Long, Intervention> confirmed = new HashMap<>();
        Set<Long> lockedInterventions = new HashSet<>();

        for (CropPlannedOperation operation : retained) {
            Intervention bound = operation.getIntervention();
            if (bound != null) {
                confirmed.put(operation.getId(), bound);
                lockedInterventions.add(bound.getId());
            }
        }

        // ------------------------------------------------------------
        // 3. Inférer le reste.
        // ------------------------------------------------------------
        List<ItineraryMatcher.PlannedRef> open = retained.stream()
                .filter(operation -> !confirmed.containsKey(operation.getId()))
                .filter(operation -> !isSettled(operation))
                .map(operation -> new ItineraryMatcher.PlannedRef(
                        operation.getId(), operation.getType(), resolved.get(operation.getId())))
                .toList();

        List<ItineraryMatcher.ActualRef> free = actual.stream()
                .filter(intervention -> !lockedInterventions.contains(intervention.getId()))
                .map(intervention -> new ItineraryMatcher.ActualRef(
                        intervention.getId(), intervention.getType(), dayOf(intervention)))
                .toList();

        Map<Long, Intervention> byId = new HashMap<>();
        actual.forEach(intervention -> byId.put(intervention.getId(), intervention));

        Map<Long, ItineraryMatcher.Match> inferred = new HashMap<>();
        matcher.match(open, free).forEach(match -> inferred.put(match.operationId(), match));

        // ------------------------------------------------------------
        // 4. Rendre.
        // ------------------------------------------------------------
        List<PlannedOperationResponse> lines = new ArrayList<>();
        for (CropPlannedOperation operation : retained) {
            LocalDate date = resolved.get(operation.getId());
            Intervention bound = confirmed.get(operation.getId());
            ItineraryMatcher.Match match = inferred.get(operation.getId());

            if (bound == null && match != null) {
                bound = byId.get(match.interventionId());
            }
            lines.add(toResponse(operation, date, bound, match, today));
        }

        // Les non datables en fin de liste : les intercaler à une position arbitraire
        // ferait lire une séquence qui n'a pas été planifiée.
        lines.sort(Comparator.comparing(PlannedOperationResponse::getResolvedDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return summarise(crop, plantingDate, lines, missing);
    }

    // ============================================================
    // Ligne à ligne
    // ============================================================

    private LocalDate resolveDate(CropPlannedOperation operation, LocalDate plantingDate) {
        if (operation.getPlannedOn() != null) {
            return operation.getPlannedOn();
        }
        if (operation.getDaysAfterPlanting() == null || plantingDate == null) {
            return null;
        }
        return plantingDate.plusDays(operation.getDaysAfterPlanting());
    }

    private boolean isSettled(CropPlannedOperation operation) {
        PlannedOperationStatus status = PlannedOperationStatus.from(operation.getStatus());
        return status == PlannedOperationStatus.ABANDONNEE;
    }

    private LocalDate dayOf(Intervention intervention) {
        return intervention.getPerformedAt() == null
                ? null
                : intervention.getPerformedAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private PlannedOperationResponse toResponse(CropPlannedOperation operation,
                                                LocalDate resolvedDate,
                                                Intervention bound,
                                                ItineraryMatcher.Match inferred,
                                                LocalDate today) {

        InterventionType type = InterventionType.from(operation.getType());
        PlannedOperationStatus status = PlannedOperationStatus.from(operation.getStatus());

        boolean confirmed = operation.getIntervention() != null;
        MatchConfidence confidence = confirmed
                ? firstNonNull(MatchConfidence.from(operation.getMatchConfidence()),
                               MatchConfidence.MANUELLE)
                : (inferred == null ? null : inferred.confidence());

        Integer gap = gapOf(resolvedDate, bound, inferred);

        // Le retard ne se pose que sur une opération encore en attente ET datable.
        // Sur une opération abandonnée ou déjà satisfaite, la question n'a pas de sens :
        // rendre `false` ferait croire qu'elle a été traitée à temps.
        Boolean late = null;
        Integer lateByDays = null;
        if (bound == null && resolvedDate != null && today != null
                && (status == null || status == PlannedOperationStatus.PREVUE)) {
            late = resolvedDate.isBefore(today);
            if (Boolean.TRUE.equals(late)) {
                lateByDays = (int) ChronoUnit.DAYS.between(resolvedDate, today);
            }
        }

        return PlannedOperationResponse.builder()
                .id(operation.getId())
                .cropId(operation.getCrop() == null ? null : operation.getCrop().getId())
                .type(operation.getType())
                .typeLabel(type == null ? null : type.getLabel())
                .label(operation.getLabel())
                .plannedOn(operation.getPlannedOn())
                .daysAfterPlanting(operation.getDaysAfterPlanting())
                .resolvedDate(resolvedDate)
                .growthStage(operation.getGrowthStage())
                .product(operation.getProduct())
                .dose(operation.getDose())
                .unit(operation.getUnit())
                .dosage(dosage(operation))
                .estimatedCost(operation.getEstimatedCost())
                .status(operation.getStatus())
                .statusLabel(status == null ? null : status.getLabel())
                .late(late)
                .lateByDays(lateByDays)
                .interventionId(bound == null ? null : bound.getId())
                .interventionPerformedAt(bound == null ? null : bound.getPerformedAt())
                .interventionCost(bound == null ? null : bound.getCost())
                .matchConfidence(confidence == null ? null : confidence.name())
                .matchConfidenceLabel(confidence == null ? null : confidence.getLabel())
                .matchConfirmed(bound == null ? null : confirmed)
                .matchGapDays(gap)
                .matchedAt(operation.getMatchedAt())
                .matchStatement(matchStatement(bound, confirmed, confidence, gap, late, lateByDays))
                .createdAt(operation.getCreatedAt())
                .updatedAt(operation.getUpdatedAt())
                .build();
    }

    private Integer gapOf(LocalDate resolvedDate, Intervention bound,
                          ItineraryMatcher.Match inferred) {
        if (inferred != null) {
            return (int) inferred.gapDays();
        }
        if (bound == null || resolvedDate == null || bound.getPerformedAt() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(resolvedDate, dayOf(bound));
    }

    /**
     * Formulation du rapprochement, ou de son absence.
     *
     * <p>Toujours renseignée : « aucune intervention rapprochée » est une information,
     * un blanc obligerait le client à l'interpréter.
     */
    private String matchStatement(Intervention bound, boolean confirmed,
                                  MatchConfidence confidence, Integer gap,
                                  Boolean late, Integer lateByDays) {

        if (bound == null) {
            if (Boolean.TRUE.equals(late)) {
                return String.format(FR,
                        "Aucune intervention rapprochée, et la date prévue est dépassée de "
                                + "%d jour(s). Soit l'opération n'a pas été faite, soit elle "
                                + "n'a pas été saisie.", lateByDays == null ? 0 : lateByDays);
            }
            return "Aucune intervention rapprochée pour l'instant.";
        }

        if (confirmed) {
            return gap == null
                    ? "Rapprochement confirmé manuellement."
                    : String.format(FR, "Rapprochement confirmé manuellement (%+d jour(s) "
                            + "par rapport à la date prévue).", gap);
        }

        String qualifier = confidence == MatchConfidence.EXACTE
                ? "Rapprochement quasi certain"
                : "Rapprochement probable";

        if (gap == null || gap == 0) {
            return qualifier + " : une intervention du même type a eu lieu à la date prévue. "
                    + "Inféré par le système, non confirmé.";
        }
        return String.format(FR,
                "%s : une intervention du même type a eu lieu %d jour(s) %s la date prévue. "
                        + "Inféré par le système, non confirmé.",
                qualifier, Math.abs(gap), gap > 0 ? "après" : "avant");
    }

    private String dosage(CropPlannedOperation operation) {
        if (operation.getDose() == null) {
            return null;
        }
        String unit = operation.getUnit() == null || operation.getUnit().isBlank()
                ? "" : " " + operation.getUnit().trim();
        return String.format(FR, "%.2f%s", operation.getDose(), unit);
    }

    // ============================================================
    // Synthèse
    // ============================================================

    private CropItinerary summarise(Crop crop, LocalDate plantingDate,
                                    List<PlannedOperationResponse> lines,
                                    List<String> missing) {

        int matched = (int) lines.stream()
                .filter(line -> line.getInterventionId() != null).count();
        int late = (int) lines.stream()
                .filter(line -> Boolean.TRUE.equals(line.getLate())).count();

        BigDecimal estimated = sum(lines, PlannedOperationResponse::getEstimatedCost);
        BigDecimal spent = sum(lines, PlannedOperationResponse::getInterventionCost);

        // Un écart calculé contre zéro ferait passer une absence de saisie pour une
        // économie — c'est la lecture la plus flatteuse, et la plus fausse.
        BigDecimal variance = estimated == null || spent == null
                ? null : spent.subtract(estimated);

        if (estimated == null && !lines.isEmpty()) {
            missing.add("Aucune opération ne porte de coût estimé : le coût prévisionnel "
                    + "de la campagne ne peut pas être calculé.");
        }

        // 0 % sur un itinéraire vide laisserait croire que rien n'a été fait, alors que
        // rien n'a été planifié.
        Double completion = lines.isEmpty()
                ? null
                : Math.round(matched * 1000d / lines.size()) / 10d;

        return CropItinerary.builder()
                .cropId(crop == null ? null : crop.getId())
                .plotId(crop == null || crop.getPlot() == null ? null : crop.getPlot().getId())
                .plotName(crop == null || crop.getPlot() == null ? null : crop.getPlot().getName())
                .cropName(crop == null ? null : Culture.canonical(crop.getCropName()))
                .plantingDate(plantingDate)
                .operations(lines)
                .operationCount(lines.size())
                .matchedCount(matched)
                .lateCount(late)
                .completionRate(completion)
                .totalEstimatedCost(estimated)
                .totalActualCost(spent)
                .costVariance(variance)
                .summary(summaryOf(lines.size(), matched, late, estimated, spent, variance))
                .limitation(LIMITATION)
                .missingData(missing)
                .generatedAt(Instant.now())
                .build();
    }

    private String summaryOf(int total, int matched, int late,
                             BigDecimal estimated, BigDecimal spent, BigDecimal variance) {

        if (total == 0) {
            return "Aucune opération planifiée sur cette campagne. L'itinéraire technique "
                    + "permet de prévoir les passages et d'en suivre la réalisation.";
        }

        StringBuilder text = new StringBuilder(String.format(FR,
                "%d opération(s) planifiée(s), %d rapprochée(s) d'une intervention réelle",
                total, matched));

        if (late > 0) {
            text.append(String.format(FR, ", %d en retard", late));
        }
        text.append('.');

        if (estimated != null) {
            text.append(String.format(FR, " Coût prévu : %s.", estimated.toPlainString()));
        }
        if (variance != null) {
            int sign = variance.signum();
            text.append(sign == 0
                    ? " Coût constaté identique au prévisionnel."
                    : String.format(FR, " Coût constaté : %s, soit %s de %s.",
                            spent.toPlainString(),
                            sign > 0 ? "un dépassement" : "une économie",
                            variance.abs().toPlainString()));
        }
        return text.toString();
    }

    private BigDecimal sum(List<PlannedOperationResponse> lines,
                           java.util.function.Function<PlannedOperationResponse, BigDecimal> field) {

        BigDecimal total = null;
        for (PlannedOperationResponse line : lines) {
            BigDecimal value = field.apply(line);
            if (value != null) {
                total = total == null ? value : total.add(value);
            }
        }
        return total;
    }

    private <T> T firstNonNull(T first, T fallback) {
        return first != null ? first : fallback;
    }
}
