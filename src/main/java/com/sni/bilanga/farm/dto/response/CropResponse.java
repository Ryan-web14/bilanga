package com.sni.bilanga.farm.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CropResponse {

    private Long id;
    private Long plotId;
    private String plotName;
    /** Forme canonique, en majuscules : identique à ce que le client envoie. */
    private String cropName;

    private String cropNameLabel;
    private String variety;
    private String seedLot;
    private LocalDate plantingDate;

    /** Durée retenue pour le cycle : celle saisie, ou la référence de l'espèce. */
    private Integer cycleDurationDays;

    /** Saisie, ou déduite de la plantation et de la durée du cycle. */
    private LocalDate expectedHarvestDate;

    private Double plantedArea;
    private Integer plantDensity;

    private String growthStage;
    private String growthStageLabel;
    private String status;
    private String statusLabel;

    /**
     * Vrai quand le stade renvoyé a été <strong>recalculé</strong> depuis la date
     * de plantation et diffère de celui enregistré. L'exposer évite au client de
     * s'étonner qu'une valeur qu'il a saisie ait changé.
     */
    private Boolean growthStageAutoResolved;

    /** Âge de la plantation, calculé plutôt que laissé à la charge du client. */
    private Integer daysSincePlanting;

    /** Jours restants avant la récolte attendue ; négatif si le terme est passé. */
    private Integer daysToHarvest;

    /** Avancement du cycle, de 0 à 1. */
    private Double cycleProgress;

    private Instant createdAt;
}
