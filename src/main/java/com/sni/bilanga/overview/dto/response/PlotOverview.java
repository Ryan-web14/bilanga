package com.sni.bilanga.overview.dto.response;


import com.sni.bilanga.diagnosis.dto.response.AlertResponse;
import com.sni.bilanga.diagnosis.dto.response.DiagnosticHistoryResponse;
import com.sni.bilanga.iot.dto.response.SensorReadingResponse;
import com.sni.bilanga.knowledge.dto.response.DiseaseRisk;
import com.sni.bilanga.knowledge.dto.response.IndicatorSet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * État complet d'une parcelle en une seule réponse.
 *
 * Rassemble ce qui exigeait auparavant quatre appels distincts — dernier relevé,
 * dernier diagnostic, alertes ouvertes, indicateurs. Une interface n'a plus à
 * orchestrer ces requêtes ni à en réconcilier les résultats.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PlotOverview {

    // ---------- Parcelle ----------
    private Long plotId;
    private String plotName;
    private String location;
    private String soilType;
    private Double area;
    private String plotStatus;

    // ---------- Culture en cours ----------
    private String cropName;
    private String variety;
    private LocalDate plantingDate;
    private String growthStage;

    /**
     * Nombre de jours écoulés depuis la plantation.
     * Entier court volontairement : les entiers 64 bits sont transmis en
     * chaînes, ce qui n'aurait pas de sens pour une durée.
     */
    private Integer daysSincePlanting;

    // ---------- Matériel ----------
    private Integer deviceCount;

    /** Charge du boîtier le moins bien pourvu : c'est lui qui tombera le premier. */
    private Integer lowestBatteryLevel;

    /** ACTIF | SILENCIEUX | AUCUN */
    private String deviceStatus;

    // ---------- Dernier relevé ----------
    private SensorReadingResponse latestReading;

    /** Ancienneté du relevé. Une donnée trop vieille rend le diagnostic caduc. */
    private Integer readingAgeMinutes;

    private IndicatorSet indicators;

    // ---------- Diagnostic ----------
    private DiagnosticHistoryResponse latestDiagnostic;

    private List<DiseaseRisk> risks;

    // ---------- Alertes ----------
    private Integer openAlertCount;
    private List<AlertResponse> alerts;

    // ---------- Synthèse ----------
    /** SANS_DONNEES | NORMAL | VIGILANCE | ALERTE | CRITIQUE */
    private String overallStatus;

    /** État de la parcelle en une phrase. */
    private String summary;

    private Instant generatedAt;
}
