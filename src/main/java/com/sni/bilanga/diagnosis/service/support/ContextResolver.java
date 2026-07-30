package com.sni.bilanga.diagnosis.service.support;


import com.sni.bilanga.diagnosis.dto.response.DiagnosisContext;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.farm.service.interfaces.CropService;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.iot.service.interfaces.SensorReadingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Complète les informations absentes d'une demande de diagnostic : la culture
 * et son stade sont déduits de la culture en cours sur la parcelle, le relevé
 * du dernier enregistrement disponible.
 */
@Component
@RequiredArgsConstructor
public class ContextResolver {

    private final PlotService plotService;
    private final CropService cropService;
    private final SensorReadingService sensorReadingService;

    public DiagnosisContext resolve(Long plotId, String cropName, Long readingId, boolean readingRequired) {
        Plot plot = plotService.require(plotId);

        // Le stade est réaligné sur la date de plantation ICI, à l'endroit exact
        // où il va servir. Il est saisi une fois et jamais corrigé : sans ce
        // recalcul, une tomate plantée en mars reste « LEVEE » jusqu'à la
        // récolte, et CropRequirementResolver applique alors les seuils d'un
        // stade que la plante a quitté depuis des mois.
        Optional<Crop> activeCrop = cropService.findActiveCrop(plotId)
                .map(cropService::refreshGrowthStage);

        boolean cropResolved = false;
        String crop = clean(cropName);
        if (crop == null) {
            crop = activeCrop.map(Crop::getCropName)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Aucune culture en cours sur la parcelle " + plotId
                                    + " : précisez la culture ou enregistrez-en une."));
            cropResolved = true;
        }

        // Le stade provient toujours de la culture enregistrée : il n'a de sens
        // que rapporté à une plantation datée, jamais fourni au coup par coup.
        String finalCrop = crop;
        String growthStage = activeCrop
                .filter(c -> finalCrop.equalsIgnoreCase(c.getCropName()))
                .map(Crop::getGrowthStage)
                .map(this::normalizeStage)
                .orElse(null);

        boolean readingResolved = false;
        SensorReading reading;
        if (readingId != null) {
            reading = sensorReadingService.require(readingId);
        } else {
            Optional<SensorReading> latest = sensorReadingService.findLatest(plotId);
            reading = latest.orElse(null);
            readingResolved = latest.isPresent();
        }

        if (readingRequired && reading == null) {
            throw new ResourceNotFoundException(
                    "Aucun relevé de capteurs disponible pour la parcelle " + plotId + ".");
        }

        return DiagnosisContext.builder()
                .plot(plot)
                .cropName(crop)
                .growthStage(growthStage)
                .reading(reading)
                .cropResolved(cropResolved)
                .readingResolved(readingResolved)
                .build();
    }

    /**
     * Un paramètre transmis deux fois arrive concaténé ("tomate,tomate").
     * La mise en minuscules aligne la valeur sur la base de connaissance et
     * sur les encodeurs du modèle tabulaire.
     */
    private String clean(String cropName) {
        if (cropName == null || cropName.isBlank()) return null;
        String value = cropName.contains(",") ? cropName.split(",")[0] : cropName;
        return value.trim().toLowerCase(Locale.FRANCE);
    }

    private String normalizeStage(String growthStage) {
        return growthStage == null || growthStage.isBlank()
                ? null
                : growthStage.trim().toUpperCase(Locale.FRANCE);
    }
}
