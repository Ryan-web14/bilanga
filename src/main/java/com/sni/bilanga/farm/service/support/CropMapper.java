package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.CropStatus;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.GrowthStage;
import com.sni.bilanga.farm.dto.response.CropResponse;
import com.sni.bilanga.farm.model.Crop;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class CropMapper {

    private final GrowthStageResolver growthStageResolver;

    public CropResponse toResponse(Crop crop) {
        // Le stade renvoyé est celui d'aujourd'hui, pas celui de la dernière
        // saisie : c'est le second qui induisait en erreur, en présentant comme
        // actuelle une information périmée depuis des mois.
        GrowthStage computed = growthStageResolver.stageFor(crop);
        GrowthStage stored = GrowthStage.from(crop.getGrowthStage());
        GrowthStage effective = computed == null ? stored : computed;

        CropStatus status = CropStatus.from(crop.getStatus());
        int cycle = growthStageResolver.cycleDays(crop.getCropName(), crop.getCycleDurationDays());
        Integer daysSincePlanting = daysSince(crop.getPlantingDate());

        return CropResponse.builder()
                .id(crop.getId())
                .plotId(crop.getPlot().getId())
                .plotName(crop.getPlot().getName())
                .cropName(Culture.canonical(crop.getCropName()))
                .cropNameLabel(Culture.labelOf(crop.getCropName()))
                .variety(crop.getVariety())
                .seedLot(crop.getSeedLot())
                .plantingDate(crop.getPlantingDate())
                .cycleDurationDays(cycle)
                .expectedHarvestDate(growthStageResolver.expectedHarvestDate(crop))
                .plantedArea(crop.getPlantedArea())
                .plantDensity(crop.getPlantDensity())
                .growthStage(effective == null ? null : effective.name())
                .growthStageLabel(effective == null ? null : effective.getLabel())
                .growthStageAutoResolved(computed != null && computed != stored)
                .status(crop.getStatus())
                .statusLabel(status == null ? null : status.getLabel())
                .daysSincePlanting(daysSincePlanting)
                .daysToHarvest(growthStageResolver.daysToHarvest(crop))
                .cycleProgress(progress(daysSincePlanting, cycle))
                .createdAt(crop.getCreatedAt())
                .build();
    }

    private Integer daysSince(LocalDate plantingDate) {
        return plantingDate == null
                ? null
                : (int) ChronoUnit.DAYS.between(plantingDate, LocalDate.now());
    }

    /** Borné à 1 : au-delà du cycle, l'avancement n'a plus de sens à dépasser 100 %. */
    private Double progress(Integer daysSincePlanting, int cycleDays) {
        if (daysSincePlanting == null || cycleDays <= 0) {
            return null;
        }
        double ratio = (double) daysSincePlanting / cycleDays;
        return Math.round(Math.max(0d, Math.min(1d, ratio)) * 100d) / 100d;
    }
}
