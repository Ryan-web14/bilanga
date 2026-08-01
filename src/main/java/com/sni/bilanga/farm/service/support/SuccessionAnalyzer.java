package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.CropClosureReason;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.farm.dto.response.CropComparison;
import com.sni.bilanga.farm.dto.response.PlotSuccession;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lit une parcelle comme une <strong>suite</strong> de campagnes, et non comme un tas.
 *
 * <h2>Ce que cela met au jour</h2>
 *
 * <p>Le système stockait toutes les campagnes sans jamais les présenter dans l'ordre.
 * Trois informations agronomiques réelles restaient donc invisibles alors qu'elles se
 * dérivaient entièrement de données déjà présentes :
 *
 * <ul>
 *   <li>les <strong>intervalles de sol nu</strong> — une jachère de trois semaines et
 *       une de trois ans ne se lisent pas pareil ;</li>
 *   <li>la <strong>monoculture</strong> — même espèce deux campagnes de suite, ce qui
 *       épuise le sol et concentre les ravageurs spécifiques ;</li>
 *   <li>le <strong>précédent cultural</strong>, qui conditionne ce qu'on peut semer.</li>
 * </ul>
 *
 * <p><strong>Aucune donnée nouvelle, aucune migration.</strong> Tout se déduit de
 * l'ordre des dates de plantation — et c'est pourquoi aucune colonne
 * {@code previous_crop_id} n'est stockée : un pointeur se périme dès qu'on corrige une
 * date ou qu'on saisit après coup une campagne oubliée. Le tri, lui, reste juste.
 *
 * <p>Sans état ni transaction.
 */
@Component
public class SuccessionAnalyzer {

    private static final Locale FR = Locale.FRANCE;

    /**
     * En deçà, l'intervalle entre deux campagnes n'est pas une jachère : c'est le temps
     * de préparer le sol. Le signaler noierait les vrais repos sous du bruit.
     */
    private static final int MIN_FALLOW_DAYS = 21;

    private static final String SUCCESSION_LIMITATION =
            "Cet historique décrit ce qui a été SAISI, non ce qui a poussé. Une campagne "
            + "non déclarée laisse un intervalle que rien ne distingue d'une vraie "
            + "jachère, et une date de plantation approximative décale tous les "
            + "intervalles qui suivent. À confronter à ce que vous savez de la parcelle.";

    private static final String COMPARISON_LIMITATION =
            "Un écart entre deux campagnes constate une évolution, il n'en donne pas la "
            + "cause. La météo de la saison, la variété, la qualité du lot de semence et "
            + "l'attention portée à la parcelle varient toutes ensemble, et une "
            + "exploitation ne fournit pas l'échantillon qui permettrait de les démêler. "
            + "Cette mise en regard sert à orienter l'observation, pas à conclure.";

    // ============================================================
    // Succession
    // ============================================================

    /**
     * @param crops campagnes de la parcelle, <strong>de la plus récente à la plus
     *              ancienne</strong> — l'ordre du dépôt
     */
    public PlotSuccession analyze(Plot plot, List<Crop> crops) {
        List<String> missing = new ArrayList<>();

        // Une erreur de saisie n'a jamais occupé le sol : la compter fausserait le
        // précédent cultural et fabriquerait une jachère qui n'a pas existé.
        List<Crop> retained = crops == null ? List.of() : crops.stream()
                .filter(crop -> !isDataEntryError(crop))
                .toList();

        List<PlotSuccession.Campaign> campaigns = new ArrayList<>();
        for (int index = 0; index < retained.size(); index++) {
            // Le « précédent » est le suivant dans la liste, puisqu'elle décroît.
            Crop previous = index + 1 < retained.size() ? retained.get(index + 1) : null;
            campaigns.add(toCampaign(retained.get(index), previous, missing));
        }

        return PlotSuccession.builder()
                .plotId(plot == null ? null : plot.getId())
                .plotName(plot == null ? null : plot.getName())
                .plotCode(plot == null ? null : plot.getPlotCode())
                .campaignCount(campaigns.size())
                .campaigns(campaigns)
                .fallowPeriods(fallowPeriodsOf(retained))
                .monocultureWarnings(monocultureWarningsOf(retained))
                .limitation(SUCCESSION_LIMITATION)
                .missingData(missing)
                .generatedAt(Instant.now())
                .build();
    }

    private boolean isDataEntryError(Crop crop) {
        CropClosureReason reason = CropClosureReason.from(crop.getClosureReason());
        return reason != null && reason.isExcludedFromHistory();
    }

    private PlotSuccession.Campaign toCampaign(Crop crop, Crop previous, List<String> missing) {
        LocalDate end = endDateOf(crop);
        boolean estimated = crop.getActualEndDate() == null && end != null;

        if (estimated) {
            missing.add(String.format(FR,
                    "La campagne de %s plantée le %s n'a pas de date de fin réelle : "
                            + "la date de récolte prévue est utilisée à la place, et les "
                            + "intervalles calculés autour sont donc approximatifs.",
                    Culture.canonical(crop.getCropName()), crop.getPlantingDate()));
        }

        CropClosureReason reason = CropClosureReason.from(crop.getClosureReason());

        return PlotSuccession.Campaign.builder()
                .cropId(crop.getId())
                .cropName(Culture.canonical(crop.getCropName()))
                .variety(crop.getVariety())
                .plantingDate(crop.getPlantingDate())
                .endDate(end)
                .endDateIsEstimated(estimated)
                .durationDays(daysBetween(crop.getPlantingDate(), end))
                .daysSincePrevious(daysBetween(endDateOf(previous), crop.getPlantingDate()))
                .status(crop.getStatus())
                .closureReason(crop.getClosureReason())
                .closureReasonLabel(reason == null ? null : reason.getLabel())
                .harvested(reason == null ? null : reason.isHarvested())
                .plantedArea(crop.getPlantedArea())
                .frozenEconomics(crop.getEconomicsSnapshot())
                .build();
    }

    /**
     * Fin retenue : le constat s'il existe, la prévision sinon.
     *
     * <p>L'ordre est celui de la fiabilité. Employer d'abord la prévision reviendrait à
     * préférer un objectif calculé à un fait observé.
     */
    private LocalDate endDateOf(Crop crop) {
        if (crop == null) {
            return null;
        }
        return crop.getActualEndDate() != null
                ? crop.getActualEndDate()
                : crop.getExpectedHarvestDate();
    }

    /**
     * Les périodes de sol nu.
     *
     * <p>Un chevauchement — fin postérieure à la plantation suivante — donne un
     * intervalle négatif. Il est <strong>signalé, pas écrasé</strong> : c'est une
     * incohérence de saisie qu'il faut voir, et la masquer à zéro ferait disparaître le
     * seul indice qu'on a d'une erreur.
     */
    private List<PlotSuccession.FallowPeriod> fallowPeriodsOf(List<Crop> crops) {
        List<PlotSuccession.FallowPeriod> periods = new ArrayList<>();

        for (int index = 0; index + 1 < crops.size(); index++) {
            Crop later = crops.get(index);
            Crop earlier = crops.get(index + 1);

            LocalDate from = endDateOf(earlier);
            LocalDate to = later.getPlantingDate();
            Integer days = daysBetween(from, to);

            if (days == null || days < MIN_FALLOW_DAYS) {
                continue;
            }

            periods.add(PlotSuccession.FallowPeriod.builder()
                    .from(from)
                    .to(to)
                    .days(days)
                    .previousCrop(Culture.canonical(earlier.getCropName()))
                    .nextCrop(Culture.canonical(later.getCropName()))
                    .build());
        }
        return periods;
    }

    /**
     * Cultures répétées d'une campagne à la suivante.
     *
     * <p>Signal agronomique réel, et non une coquetterie : la monoculture épuise les
     * mêmes réserves du sol et concentre les ravageurs spécifiques d'une espèce. La
     * rotation est le premier levier dont dispose un exploitant, et le système avait
     * l'information sans jamais la dire.
     */
    private List<String> monocultureWarningsOf(List<Crop> crops) {
        Map<String, Integer> streaks = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (int index = 0; index + 1 < crops.size(); index++) {
            String current = Culture.canonical(crops.get(index).getCropName());
            String previous = Culture.canonical(crops.get(index + 1).getCropName());

            if (current == null || !current.equalsIgnoreCase(previous)) {
                continue;
            }
            streaks.merge(current, 2, (existing, added) -> existing + 1);
        }

        streaks.forEach((crop, count) -> warnings.add(String.format(FR,
                "%s a été cultivé %d campagnes de suite sur cette parcelle. La "
                        + "monoculture épuise les mêmes réserves du sol et concentre les "
                        + "ravageurs propres à l'espèce. Envisagez une rotation.",
                crop, count)));

        return warnings;
    }

    // ============================================================
    // Comparaison N vs N-1
    // ============================================================

    /**
     * Compare deux campagnes de la même culture, sur leurs bilans <strong>figés</strong>.
     *
     * <p>Les bilans figés, et non recalculés : c'est ce qui rend la comparaison stable.
     * Recalculer les deux côtés à chaque appel les ferait bouger sous les pieds de
     * l'utilisateur dès qu'une récolte est saisie — et deux consultations successives
     * donneraient des écarts différents.
     */
    public CropComparison compare(Plot plot, Crop current, Crop previous) {
        List<String> missing = new ArrayList<>();

        PlotSuccession.Campaign currentCampaign = toCampaign(current, previous, missing);
        PlotSuccession.Campaign previousCampaign =
                previous == null ? null : toCampaign(previous, null, missing);

        List<CropComparison.Metric> metrics = previous == null
                ? List.of()
                : metricsOf(current, previous, missing);

        return CropComparison.builder()
                .plotId(plot == null ? null : plot.getId())
                .plotName(plot == null ? null : plot.getName())
                .cropName(Culture.canonical(current.getCropName()))
                .current(currentCampaign)
                .previous(previousCampaign)
                .comparable(previous != null)
                .metrics(metrics)
                .summary(summaryOf(current, previous, metrics))
                .limitation(COMPARISON_LIMITATION)
                .missingData(missing)
                .generatedAt(Instant.now())
                .build();
    }

    private List<CropComparison.Metric> metricsOf(Crop current, Crop previous,
                                                   List<String> missing) {

        Map<String, Object> now = current.getEconomicsSnapshot();
        Map<String, Object> then = previous.getEconomicsSnapshot();

        if (now == null || now.isEmpty() || then == null || then.isEmpty()) {
            missing.add("Une des deux campagnes n'a pas de bilan arrêté : elle a été "
                    + "close avant que le système ne fige un bilan, ou n'est pas encore "
                    + "close. Les indicateurs économiques ne sont donc pas comparables.");
            return List.of();
        }

        List<CropComparison.Metric> metrics = new ArrayList<>();

        metrics.add(metric("yieldPerHectare", "Rendement", "kg/ha", then, now, true));
        metrics.add(metric("marginPerHectare", "Marge à l'hectare", null, then, now, true));
        metrics.add(metric("grossRevenue", "Produit brut", null, then, now, true));
        // Les charges ne sont PAS orientées : elles peuvent monter pour de bonnes
        // raisons — un traitement de plus qui sauve la récolte.
        metrics.add(metric("totalCost", "Charges", null, then, now, null));
        metrics.add(metric("uptakeRate", "Taux de suivi des conseils", "%", then, now, true));

        return metrics.stream().filter(java.util.Objects::nonNull).toList();
    }

    private CropComparison.Metric metric(String key, String label, String unit,
                                         Map<String, Object> then, Map<String, Object> now,
                                         Boolean higherIsBetter) {

        Double before = numberOf(then.get(key));
        Double after = numberOf(now.get(key));

        if (before == null && after == null) {
            return null;
        }

        Double change = before == null || after == null ? null : round(after - before);

        // Division par zéro : « +∞ » ne se lit pas, et un pourcentage inventé serait pire
        // que l'absence de pourcentage.
        Double changePercent = before == null || after == null || Math.abs(before) < 0.0001
                ? null
                : round((after - before) / Math.abs(before) * 100);

        Boolean better = change == null || higherIsBetter == null || change == 0
                ? null
                : (change > 0) == higherIsBetter;

        return CropComparison.Metric.builder()
                .key(key)
                .label(label)
                .unit(unit)
                .previousValue(before)
                .currentValue(after)
                .change(change)
                .changePercent(changePercent)
                .better(better)
                .statement(statementOf(label, unit, before, after, change, changePercent))
                .build();
    }

    private String statementOf(String label, String unit, Double before, Double after,
                               Double change, Double changePercent) {
        if (before == null || after == null) {
            return String.format(FR, "%s : comparaison impossible, une des deux campagnes "
                    + "ne porte pas cette valeur.", label);
        }
        String suffix = unit == null ? "" : " " + unit;

        if (change == null || change == 0) {
            return String.format(FR, "%s : inchangé à %.1f%s.", label, after, suffix);
        }
        return String.format(FR, "%s : %.1f%s contre %.1f%s (%+.1f%s%s).",
                label, after, suffix, before, suffix, change, suffix,
                changePercent == null ? "" : String.format(FR, ", %+.0f %%", changePercent));
    }

    private String summaryOf(Crop current, Crop previous,
                             List<CropComparison.Metric> metrics) {

        String crop = Culture.canonical(current.getCropName());

        if (previous == null) {
            return String.format(FR,
                    "Première campagne de %s enregistrée sur cette parcelle : il n'y a "
                            + "pas de campagne antérieure à laquelle la comparer.", crop);
        }
        if (metrics.isEmpty()) {
            return String.format(FR,
                    "Campagne de %s plantée le %s, comparée à celle du %s. Aucun "
                            + "indicateur économique n'est comparable : au moins une des "
                            + "deux n'a pas de bilan arrêté.",
                    crop, current.getPlantingDate(), previous.getPlantingDate());
        }

        long improved = metrics.stream().filter(m -> Boolean.TRUE.equals(m.getBetter())).count();
        long worsened = metrics.stream().filter(m -> Boolean.FALSE.equals(m.getBetter())).count();

        return String.format(FR,
                "Campagne de %s plantée le %s, comparée à celle du %s : %d indicateur(s) "
                        + "en progrès, %d en recul. Un écart constate une évolution, il "
                        + "n'en donne pas la cause.",
                crop, current.getPlantingDate(), previous.getPlantingDate(), improved, worsened);
    }

    // ============================================================
    // Interne
    // ============================================================

    /**
     * Les valeurs du bilan figé sont passées par {@code jsonb} : les montants y sont des
     * <strong>chaînes</strong> (les identifiants Snowflake le sont partout, et la
     * conversion s'applique globalement). Les deux formes sont donc acceptées.
     */
    private Double numberOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private Integer daysBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(from, to);
    }

    private Double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
