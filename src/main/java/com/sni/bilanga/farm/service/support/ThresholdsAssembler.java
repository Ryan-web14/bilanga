package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.GrowthStage;
import com.sni.bilanga.farm.dto.response.CropThresholds;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.knowledge.model.CropRequirement;
import com.sni.bilanga.knowledge.service.support.CropRequirementResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Expose les seuils sur lesquels le moteur agronomique juge une campagne, stade par
 * stade, <strong>avec l'origine de chaque valeur</strong>.
 *
 * <h2>Aucun calcul nouveau</h2>
 *
 * <p>{@link CropRequirementResolver#resolve} fusionne depuis toujours les seuils
 * généraux de la culture avec les surcharges de stade (V10). Cette classe se contente de
 * demander <em>les deux</em> — la base seule, puis la fusion — et de comparer champ par
 * champ pour dire d'où vient chaque valeur.
 *
 * <h2>Pourquoi l'origine compte</h2>
 *
 * <p>Sans elle, un exploitant voit le système « changer d'avis » : le même taux
 * d'humidité déclenche un conseil en fructification et pas en levée. Dire que le seuil
 * lui-même a changé transforme une incohérence apparente en information agronomique.
 *
 * <p>Sans état ni transaction.
 */
@Component
@RequiredArgsConstructor
public class ThresholdsAssembler {

    private static final Locale FR = Locale.FRANCE;

    static final String GENERALE = "GENERALE";
    static final String STADE = "STADE";

    private static final String LIMITATION =
            "Ces seuils sont ceux qu'applique le moteur agronomique, mais les valeurs "
            + "semées à l'installation sont INDICATIVES : elles n'ont pas été validées "
            + "par une source agronomique congolaise. Elles se règlent par "
            + "/knowledge/crop-requirements, et une modification peut mettre jusqu'à "
            + "trente minutes à se refléter : les tables de connaissance sont en cache.";

    private final CropRequirementResolver requirementResolver;
    private final GrowthStageResolver growthStageResolver;

    public CropThresholds assemble(Crop crop) {
        List<String> missing = new ArrayList<>();

        String cropName = crop.getCropName();
        CropRequirement baseline = requirementResolver.baseline(cropName).orElse(null);

        if (baseline == null) {
            missing.add(String.format(FR,
                    "Aucun seuil n'est enregistré pour la culture « %s » : le moteur "
                            + "agronomique ne peut donc produire aucun conseil fondé sur "
                            + "les mesures pour cette campagne.",
                    Culture.canonical(cropName)));
        }

        GrowthStage current = growthStageResolver.stageFor(crop);
        List<GrowthStageResolver.StageStart> timeline = growthStageResolver.stageTimeline(crop);

        if (timeline.isEmpty()) {
            missing.add("La campagne n'a pas de date de plantation : les stades ne peuvent "
                    + "pas être datés, et le stade en cours n'est pas déterminable.");
        }

        List<CropThresholds.StageThresholds> stages = new ArrayList<>();
        for (GrowthStageResolver.StageStart start : timeline) {
            stages.add(stageOf(cropName, baseline, start, current));
        }

        return CropThresholds.builder()
                .cropId(crop.getId())
                .plotId(crop.getPlot() == null ? null : crop.getPlot().getId())
                .plotName(crop.getPlot() == null ? null : crop.getPlot().getName())
                .cropName(Culture.canonical(cropName))
                .currentStage(current == null ? null : current.name())
                .currentStageLabel(current == null ? null : current.getLabel())
                .stages(stages)
                .limitation(LIMITATION)
                .missingData(missing)
                .generatedAt(Instant.now())
                .build();
    }

    // ============================================================
    // Un stade
    // ============================================================

    private CropThresholds.StageThresholds stageOf(String cropName,
                                                   CropRequirement baseline,
                                                   GrowthStageResolver.StageStart start,
                                                   GrowthStage current) {

        String stage = start.stage().name();
        CropRequirement effective = requirementResolver.resolve(cropName, stage).orElse(null);
        boolean overridden = requirementResolver.hasStageOverride(cropName, stage);

        List<CropThresholds.ThresholdRange> measures = effective == null
                ? List.of()
                : rangesOf(baseline, effective);

        return CropThresholds.StageThresholds.builder()
                .stage(stage)
                .stageLabel(start.stage().getLabel())
                .startsOn(start.startsOn())
                .current(current != null && current == start.stage())
                .hasStageOverride(overridden)
                .measures(measures)
                .toleranceSecheresse(effective == null ? null : effective.getToleranceSecheresse())
                .toleranceOrigin(effective == null ? null
                        : originOf(baseline == null ? null : baseline.getToleranceSecheresse(),
                                   effective.getToleranceSecheresse()))
                .build();
    }

    private List<CropThresholds.ThresholdRange> rangesOf(CropRequirement baseline,
                                                         CropRequirement effective) {
        List<CropThresholds.ThresholdRange> ranges = new ArrayList<>();

        add(ranges, baseline, effective, "humidite_sol", "Humidité du sol", "%",
                CropRequirement::getHumSolMin, CropRequirement::getHumSolMax);
        add(ranges, baseline, effective, "temperature", "Température de l'air", "°C",
                CropRequirement::getTempMin, CropRequirement::getTempMax);
        add(ranges, baseline, effective, "ph", "pH du sol", null,
                CropRequirement::getPhMin, CropRequirement::getPhMax);

        // Les seuils nutritifs n'ont pas de maximum : un excès d'azote se lit sur le
        // déséquilibre NPK, pas sur un plafond par élément.
        add(ranges, baseline, effective, "azote", "Azote", "mg/kg",
                CropRequirement::getAzoteMin, requirement -> null);
        add(ranges, baseline, effective, "phosphore", "Phosphore", "mg/kg",
                CropRequirement::getPhosphoreMin, requirement -> null);
        add(ranges, baseline, effective, "potassium", "Potassium", "mg/kg",
                CropRequirement::getPotassiumMin, requirement -> null);

        return ranges;
    }

    private void add(List<CropThresholds.ThresholdRange> ranges,
                     CropRequirement baseline, CropRequirement effective,
                     String measure, String label, String unit,
                     java.util.function.Function<CropRequirement, Double> min,
                     java.util.function.Function<CropRequirement, Double> max) {

        Double effectiveMin = min.apply(effective);
        Double effectiveMax = max.apply(effective);

        // Une mesure sans aucune borne n'est pas jugée : la rendre à zéro laisserait
        // croire à un seuil, alors qu'il n'y en a pas.
        if (effectiveMin == null && effectiveMax == null) {
            return;
        }

        String origin = baseline == null ? STADE
                : originOf(min.apply(baseline), effectiveMin) .equals(STADE)
                  || originOf(max.apply(baseline), effectiveMax).equals(STADE)
                  ? STADE : GENERALE;

        ranges.add(CropThresholds.ThresholdRange.builder()
                .measure(measure)
                .label(label)
                .unit(unit)
                .min(effectiveMin)
                .max(effectiveMax)
                .origin(origin)
                .originLabel(STADE.equals(origin)
                        ? "Propre à ce stade" : "Seuil général de la culture")
                .statement(statementOf(label, unit, effectiveMin, effectiveMax, origin))
                .build());
    }

    /**
     * {@code STADE} dès que la valeur effective diffère de la valeur générale.
     *
     * <p>Comparaison de valeurs et non de présence : une surcharge qui reprend la valeur
     * générale n'infléchit rien, et l'annoncer comme propre au stade ferait chercher une
     * nuance qui n'existe pas.
     */
    private String originOf(Double baseline, Double effective) {
        return Objects.equals(baseline, effective) ? GENERALE : STADE;
    }

    private String statementOf(String label, String unit, Double min, Double max, String origin) {
        String suffix = unit == null ? "" : " " + unit;
        String source = STADE.equals(origin)
                ? " Seuil propre à ce stade."
                : " Seuil général de la culture.";

        if (min != null && max != null) {
            return String.format(FR, "%s attendue entre %.1f%s et %.1f%s.%s",
                    label, min, suffix, max, suffix, source);
        }
        if (min != null) {
            return String.format(FR, "%s : au moins %.1f%s.%s", label, min, suffix, source);
        }
        return String.format(FR, "%s : au plus %.1f%s.%s", label, max, suffix, source);
    }
}
