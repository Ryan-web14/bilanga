package com.sni.bilanga.farm.service.support;


import com.sni.bilanga.enums.IrrigationType;
import com.sni.bilanga.enums.PlotStatus;
import com.sni.bilanga.enums.SoilType;
import com.sni.bilanga.farm.dto.response.PlotResponse;
import com.sni.bilanga.farm.model.Plot;
import org.springframework.stereotype.Component;

@Component
public class PlotMapper {

    public PlotResponse toResponse(Plot plot) {
        SoilType soilType = SoilType.from(plot.getSoilType());
        PlotStatus status = PlotStatus.from(plot.getStatus());
        IrrigationType irrigation = IrrigationType.from(plot.getIrrigationType());

        return PlotResponse.builder()
                .id(plot.getId())
                .plotCode(plot.getPlotCode())
                .name(plot.getName())
                .location(plot.getLocation())
                .latitude(plot.getLatitude())
                .longitude(plot.getLongitude())
                .altitude(plot.getAltitude())
                .geolocated(plot.getLatitude() != null && plot.getLongitude() != null)
                .soilType(plot.getSoilType())
                .soilTypeLabel(soilType == null ? null : soilType.getLabel())
                .irrigationType(plot.getIrrigationType())
                .irrigationTypeLabel(irrigation == null ? null : irrigation.getLabel())
                // Nul, et non faux, quand le moyen d'irrigation est inconnu :
                // ne pas savoir n'est pas la même chose que savoir qu'on ne peut pas.
                .waterOnDemand(irrigation == null ? null : irrigation.isWaterOnDemand())
                .area(plot.getArea())
                .status(plot.getStatus())
                .statusLabel(status == null ? null : status.getLabel())
                .userId(plot.getUser() == null ? null : plot.getUser().getId())
                .farmId(plot.getFarm() == null ? null : plot.getFarm().getId())
                .farmName(plot.getFarm() == null ? null : plot.getFarm().getName())
                .cooperativeId(cooperativeIdOf(plot))
                .cooperativeName(cooperativeNameOf(plot))
                .createdAt(plot.getCreatedAt())
                .updatedAt(plot.getUpdatedAt())
                .build();
    }

    /**
     * Les deux niveaux supérieurs sont facultatifs et indépendants : une
     * parcelle peut n'avoir ni exploitation, ou une exploitation sans
     * coopérative. Chaque étape se vérifie donc séparément — la chaîner d'un
     * trait produirait un {@code NullPointerException} sur le cas majoritaire.
     */
    private Long cooperativeIdOf(Plot plot) {
        if (plot.getFarm() == null || plot.getFarm().getCooperative() == null) {
            return null;
        }
        return plot.getFarm().getCooperative().getId();
    }

    private String cooperativeNameOf(Plot plot) {
        if (plot.getFarm() == null || plot.getFarm().getCooperative() == null) {
            return null;
        }
        return plot.getFarm().getCooperative().getName();
    }
}
