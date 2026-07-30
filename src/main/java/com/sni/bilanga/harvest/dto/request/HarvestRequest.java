package com.sni.bilanga.harvest.dto.request;

import com.sni.bilanga.enums.HarvestQuality;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HarvestRequest {

    @NotNull(message = "La parcelle est obligatoire")
    private Long plotId;

    /**
     * Culture récoltée. Déduite de la plantation en cours si elle n'est pas
     * transmise — mais obligatoire au final : une récolte qui ne se rattache à
     * aucune campagne n'entre dans aucun calcul.
     */
    private Long cropId;

    @PositiveOrZero(message = "La quantité ne peut être négative")
    private Double quantity;

    @Size(max = 20, message = "L'unité ne peut dépasser 20 caractères")
    private String unit;

    private HarvestQuality quality;

    @PastOrPresent(message = "La date de récolte ne peut être dans le futur")
    private LocalDate harvestedAt;

    /** Prix unitaire, et non montant total : le total se recalcule. */
    @DecimalMin(value = "0", message = "Le prix unitaire ne peut être négatif")
    private BigDecimal unitPrice;

    @Size(max = 3, message = "Le code monnaie fait trois caractères")
    private String currency;

    private String note;
}
