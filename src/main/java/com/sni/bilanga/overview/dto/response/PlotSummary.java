package com.sni.bilanga.overview.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Ligne de tableau de bord : l'essentiel d'une parcelle en un coup d'œil.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PlotSummary {

    private Long plotId;
    private String plotName;
    private String cropName;

    /** SANS_DONNEES | NORMAL | VIGILANCE | ALERTE | CRITIQUE */
    private String overallStatus;

    private Integer openAlertCount;
    private Instant lastReadingAt;

    /** ACTIF | SILENCIEUX | AUCUN */
    private String deviceStatus;
}
