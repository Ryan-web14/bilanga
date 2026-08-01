package com.sni.bilanga.harvest.service.support;

import com.sni.bilanga.diagnosis.repository.RecommendationRepository;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.InterventionType;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.harvest.dto.response.PlotEconomics;
import com.sni.bilanga.harvest.model.Harvest;
import com.sni.bilanga.harvest.repository.HarvestRepository;
import com.sni.bilanga.intervention.repository.InterventionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bilan économique d'une parcelle : produit, charges, marge.
 *
 * <p><strong>Ce que cela apporte.</strong> Le système disait ce qu'il fallait
 * faire, sans jamais dire si cela avait rapporté. C'est ce qui le fait passer
 * d'un assistant technique à un outil de gestion — et, pour le mémoire, ce qui
 * permet de <em>quantifier</em> l'apport de la plateforme plutôt que de le
 * postuler. Une évaluation chiffrée vaut mieux qu'une démonstration
 * fonctionnelle.
 *
 * <p><strong>Tout est recalculé, rien n'est stocké.</strong> Un total mis en
 * cache diverge dès la première correction de saisie, et personne ne sait plus
 * lequel des deux chiffres croire. Les sources — récoltes, interventions,
 * surface de la culture — sont peu volumineuses à l'échelle d'une campagne.
 *
 * <p><strong>La réserve fait partie du résultat.</strong> Le taux de suivi des
 * conseils est mis en regard du rendement ; cette mise en regard est
 * descriptive, jamais causale. Le sol, la variété, la météo et l'attention
 * générale portée à la parcelle varient ensemble. Le dire est plus solide que de
 * le taire — un jury le demanderait de toute façon.
 */
@Component
@RequiredArgsConstructor
public class MarginCalculator {

    private static final Locale FR = Locale.FRANCE;
    private static final int SCALE = 2;

    private final HarvestRepository harvestRepository;
    private final InterventionRepository interventionRepository;
    private final RecommendationRepository recommendationRepository;

    public PlotEconomics compute(Plot plot, Crop crop, LocalDate from, LocalDate to) {
        Long cropId = crop == null ? null : crop.getId();
        List<String> missing = new ArrayList<>();

        List<Harvest> harvests = harvestRepository.findForPeriod(plot.getId(), cropId, from, to);

        Instant fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Map<String, BigDecimal> costByType = new LinkedHashMap<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        int interventionCount = 0;

        for (Object[] row : interventionRepository
                .aggregateCostByType(plot.getId(), cropId, fromInstant, toInstant)) {

            String type = (String) row[0];
            BigDecimal cost = toBigDecimal(row[1]);
            long count = ((Number) row[2]).longValue();

            InterventionType parsed = InterventionType.from(type);
            costByType.put(parsed == null ? type : parsed.getLabel(), cost);
            totalCost = totalCost.add(cost);
            interventionCount += (int) count;
        }

        BigDecimal grossRevenue = grossRevenueOf(harvests, missing);
        Double totalQuantity = totalQuantityOf(harvests);
        BigDecimal margin = grossRevenue.subtract(totalCost);

        Double plantedArea = crop == null ? null : crop.getPlantedArea();
        if (plantedArea == null || plantedArea <= 0) {
            missing.add("Surface plantée non renseignée sur la culture : "
                        + "ni la marge à l'hectare ni le rendement ne peuvent être calculés, "
                        + "et la comparaison avec une autre parcelle est donc impossible.");
        }

        // Une ligne, toujours : un agrégat sans `group by` en rend une même sur un
        // ensemble vide. On la lit défensivement quand même, car un résultat vide
        // coûterait ici le bilan entier.
        List<Object[]> uptakeRows =
                recommendationRepository.uptakeSummary(plot.getId(), fromInstant, toInstant);
        Object[] uptake = uptakeRows == null || uptakeRows.isEmpty() ? null : uptakeRows.getFirst();
        int total = uptake == null || uptake.length < 1 ? 0 : intOf(uptake[0]);
        int applied = uptake == null || uptake.length < 2 ? 0 : intOf(uptake[1]);

        if (harvests.isEmpty()) {
            missing.add("Aucune récolte enregistrée sur la période : le produit brut est nul "
                        + "par absence de donnée, non parce que la parcelle n'a rien produit.");
        }
        if (interventionCount == 0) {
            missing.add("Aucune intervention enregistrée : les charges sont nulles par absence "
                        + "de saisie. La marge affichée est donc surestimée.");
        }

        PlotEconomics economics = PlotEconomics.builder()
                .plotId(plot.getId())
                .plotName(plot.getName())
                .plotCode(plot.getPlotCode())
                .cropId(cropId)
                .cropName(crop == null ? null : Culture.canonical(crop.getCropName()))
                .from(from)
                .to(to)
                .currency(currencyOf(harvests))
                .harvestCount(harvests.size())
                .totalQuantity(totalQuantity)
                .quantityUnit(unitOf(harvests))
                .grossRevenue(grossRevenue)
                .interventionCount(interventionCount)
                .totalCost(totalCost)
                .costByInterventionType(costByType)
                .margin(margin)
                .plantedArea(plantedArea)
                .marginPerHectare(perHectare(margin, plantedArea))
                .yieldPerHectare(yieldPerHectare(totalQuantity, plantedArea))
                .costRatio(costRatio(grossRevenue, totalCost))
                .recommendationCount(total)
                .appliedRecommendationCount(applied)
                .uptakeRate(total == 0 ? null : round(applied * 100d / total))
                .missingData(missing)
                .limitation(LIMITATION)
                .generatedAt(Instant.now())
                .build();

        economics.setSummary(summaryOf(economics));
        return economics;
    }

    private static final String LIMITATION =
            "Le taux de suivi des conseils et le rendement sont présentés côte à côte : "
            + "c'est un constat, pas une démonstration. Le sol, la variété, la météo de la "
            + "saison et l'attention générale portée à la parcelle varient ensemble, et une "
            + "exploitation ne fournit pas l'échantillon qui permettrait de les démêler. "
            + "Cette mise en regard sert à orienter l'observation, pas à conclure.";

    // ============================================================
    // Produit
    // ============================================================

    /**
     * {@code Σ quantité × prix unitaire}.
     *
     * <p>Une récolte sans prix est comptée pour zéro et signalée : l'ignorer
     * silencieusement donnerait une marge fausse que rien ne distinguerait d'une
     * marge juste — c'est le pire des deux cas.
     */
    private BigDecimal grossRevenueOf(List<Harvest> harvests, List<String> missing) {
        BigDecimal total = BigDecimal.ZERO;
        int unpriced = 0;

        for (Harvest harvest : harvests) {
            if (harvest.getQuantity() == null || harvest.getUnitPrice() == null) {
                unpriced++;
                continue;
            }
            total = total.add(harvest.getUnitPrice()
                    .multiply(BigDecimal.valueOf(harvest.getQuantity())));
        }

        if (unpriced > 0) {
            missing.add(unpriced + " récolte(s) sans quantité ou sans prix unitaire : "
                        + "elles ne sont pas comptées dans le produit brut, qui est donc "
                        + "sous-estimé d'autant.");
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private Double totalQuantityOf(List<Harvest> harvests) {
        double total = harvests.stream()
                .map(Harvest::getQuantity)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        return total == 0 ? null : round(total);
    }

    /**
     * Unité de la première récolte renseignée.
     *
     * Mélanger des kilogrammes et des sacs dans un même total n'aurait pas de
     * sens ; l'unité est donc indicative et l'homogénéité relève de la saisie.
     */
    private String unitOf(List<Harvest> harvests) {
        return harvests.stream()
                .map(Harvest::getUnit)
                .filter(unit -> unit != null && !unit.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String currencyOf(List<Harvest> harvests) {
        return harvests.stream()
                .map(Harvest::getCurrency)
                .filter(currency -> currency != null && !currency.isBlank())
                .findFirst()
                .orElse("XAF");
    }

    // ============================================================
    // Ratios
    // ============================================================
    private BigDecimal perHectare(BigDecimal amount, Double area) {
        if (area == null || area <= 0) {
            return null;
        }
        return amount.divide(BigDecimal.valueOf(area), SCALE, RoundingMode.HALF_UP);
    }

    private Double yieldPerHectare(Double quantity, Double area) {
        if (quantity == null || area == null || area <= 0) {
            return null;
        }
        return round(quantity / area);
    }

    /**
     * Part du produit absorbée par les charges.
     *
     * Nulle si le produit est nul : diviser par zéro donnerait l'infini, et
     * afficher « charges à l'infini » pour une parcelle simplement pas encore
     * récoltée serait absurde.
     */
    private Double costRatio(BigDecimal revenue, BigDecimal cost) {
        if (revenue.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return round(cost.divide(revenue, 4, RoundingMode.HALF_UP).doubleValue() * 100);
    }

    // ============================================================
    // Restitution
    // ============================================================
    private String summaryOf(PlotEconomics e) {
        StringBuilder text = new StringBuilder();

        text.append(String.format(FR, "Produit brut %s %s pour %s %s de charges",
                e.getGrossRevenue().toPlainString(), e.getCurrency(),
                e.getTotalCost().toPlainString(), e.getCurrency()));

        boolean profitable = e.getMargin().compareTo(BigDecimal.ZERO) >= 0;
        text.append(String.format(FR, ", soit une marge %s de %s %s.",
                profitable ? "positive" : "NÉGATIVE",
                e.getMargin().abs().toPlainString(), e.getCurrency()));

        if (e.getMarginPerHectare() != null) {
            text.append(String.format(FR, " Soit %s %s par hectare",
                    e.getMarginPerHectare().toPlainString(), e.getCurrency()));

            if (e.getYieldPerHectare() != null) {
                text.append(String.format(FR, ", pour un rendement de %.1f%s/ha",
                        e.getYieldPerHectare(),
                        e.getQuantityUnit() == null ? "" : " " + e.getQuantityUnit()));
            }
            text.append('.');
        }

        if (e.getUptakeRate() != null) {
            text.append(String.format(FR, " %d conseils sur %d ont été appliqués (%.0f %%).",
                    e.getAppliedRecommendationCount(), e.getRecommendationCount(),
                    e.getUptakeRate()));
        }

        if (!e.getMissingData().isEmpty()) {
            text.append(" ⚠ ").append(e.getMissingData().size())
                    .append(" donnée(s) manquante(s) : le bilan est incomplet.");
        }
        return text.toString();
    }

    // ============================================================
    // Conversion
    // ============================================================
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(((Number) value).doubleValue())
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private int intOf(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
