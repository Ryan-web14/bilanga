package com.sni.bilanga.diagnosis.dto.response;


import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.iot.model.SensorReading;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contexte complet d'un diagnostic, après résolution des éléments non fournis.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DiagnosisContext {

    private Plot plot;
    private String cropName;

    /** Stade de la culture en cours. Infléchit les seuils agronomiques appliqués. */
    private String growthStage;

    private SensorReading reading;

    /** Vrai si la culture a été déduite de la culture en cours sur la parcelle. */
    private boolean cropResolved;

    /** Vrai si le relevé a été déduit du dernier enregistrement de la parcelle. */
    private boolean readingResolved;
}
