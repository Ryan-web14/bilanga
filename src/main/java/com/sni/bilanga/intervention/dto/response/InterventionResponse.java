package com.sni.bilanga.intervention.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class InterventionResponse {

    private Long id;

    private Long plotId;
    private String plotName;

    private Long cropId;
    private String cropName;

    private Long recommendationId;

    /** Extrait du conseil suivi, pour situer l'action sans un appel de plus. */
    private String recommendationContent;

    private String type;
    private String typeLabel;

    private String product;
    private Double dose;
    private String unit;

    /** Dosage formulé d'un bloc : {@code 12,5 kg/ha}. */
    private String dosage;

    private BigDecimal cost;

    private Instant performedAt;

    private Long performedById;
    private String performedByName;

    private String weatherNote;
    private String note;

    /**
     * Vrai lorsque l'effet de cette intervention peut se lire sur une mesure de
     * sonde. Faux pour un traitement phytosanitaire, dont l'effet se juge sur
     * les diagnostics suivants — le dire évite de proposer une analyse d'effet
     * qui ne pourrait rien conclure.
     */
    private Boolean effectMeasurable;

    private Instant createdAt;
}
