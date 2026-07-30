package com.sni.bilanga.harvest.service.support;

import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.HarvestQuality;
import com.sni.bilanga.harvest.dto.response.HarvestResponse;
import com.sni.bilanga.harvest.model.Harvest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class HarvestMapper {

    public HarvestResponse toResponse(Harvest h) {
        HarvestQuality quality = HarvestQuality.from(h.getQuality());
        Double plantedArea = h.getCrop() == null ? null : h.getCrop().getPlantedArea();

        return HarvestResponse.builder()
                .id(h.getId())
                .plotId(h.getPlot().getId())
                .plotName(h.getPlot().getName())
                .cropId(h.getCrop().getId())
                .cropName(Culture.canonical(h.getCrop().getCropName()))
                .variety(h.getCrop().getVariety())
                .quantity(h.getQuantity())
                .unit(h.getUnit())
                .quality(h.getQuality())
                .qualityLabel(quality == null ? null : quality.getLabel())
                .harvestedAt(h.getHarvestedAt())
                .unitPrice(h.getUnitPrice())
                // Calculé ici plutôt que côté client : trois clients feraient
                // trois arrondis différents sur le même chiffre.
                .grossRevenue(grossRevenue(h))
                .currency(h.getCurrency())
                .yieldPerHectare(yieldPerHectare(h.getQuantity(), plantedArea))
                .note(h.getNote())
                .createdAt(h.getCreatedAt())
                .build();
    }

    private BigDecimal grossRevenue(Harvest h) {
        if (h.getQuantity() == null || h.getUnitPrice() == null) {
            return null;
        }
        return h.getUnitPrice()
                .multiply(BigDecimal.valueOf(h.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Seul chiffre comparable entre deux parcelles de tailles différentes. */
    private Double yieldPerHectare(Double quantity, Double plantedArea) {
        if (quantity == null || plantedArea == null || plantedArea <= 0) {
            return null;
        }
        return Math.round(quantity / plantedArea * 100d) / 100d;
    }
}
