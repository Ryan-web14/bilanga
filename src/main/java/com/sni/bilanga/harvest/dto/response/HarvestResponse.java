package com.sni.bilanga.harvest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class HarvestResponse {

    private Long id;

    private Long plotId;
    private String plotName;

    private Long cropId;
    private String cropName;
    private String variety;

    private Double quantity;
    private String unit;

    private String quality;
    private String qualityLabel;

    private LocalDate harvestedAt;

    private BigDecimal unitPrice;

    /** {@code quantité × prix unitaire}, calculé ici pour que chacun lise le même chiffre. */
    private BigDecimal grossRevenue;

    private String currency;

    /**
     * Rendement à l'hectare, calculé depuis la surface plantée de la culture.
     * Nul si la surface n'a pas été renseignée — c'est le seul chiffre qui
     * permette de comparer deux parcelles de tailles différentes.
     */
    private Double yieldPerHectare;

    private String note;
    private Instant createdAt;
}
