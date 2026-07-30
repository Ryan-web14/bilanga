package com.sni.bilanga.farm.dto.request;

import com.sni.bilanga.enums.InterventionType;
import com.sni.bilanga.enums.PlannedOperationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une opération de l'itinéraire technique.
 *
 * <p><strong>Seul {@code type} est obligatoire, et l'une des deux datations.</strong>
 * Un itinéraire se saisit souvent en amont de la campagne, avec des produits et des
 * doses qui ne seront arrêtés qu'au moment de faire — refuser la ligne parce que la
 * dose manque reviendrait à n'avoir aucun plan plutôt qu'un plan incomplet.
 *
 * <p>La contrainte « {@code plannedOn} ou {@code daysAfterPlanting} » est tenue par le
 * service, avec un message métier, et par {@code chk_planned_op_when} en base. Une
 * opération qu'on ne sait pas dater n'est pas un plan : c'est une note.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PlannedOperationRequest {

    /**
     * Vocabulaire de {@link InterventionType}, et non un vocabulaire propre : le
     * rapprochement prévu ↔ réalisé se fait sur ce champ.
     */
    @NotNull(message = "Le type d'opération est obligatoire.")
    private InterventionType type;

    @Size(max = 150, message = "L'intitulé ne peut dépasser 150 caractères.")
    private String label;

    /** Date ferme. Exclusive ou complémentaire de {@link #daysAfterPlanting}. */
    private LocalDate plannedOn;

    /**
     * Position dans le cycle.
     *
     * <p>C'est la forme qui <strong>survit au clonage</strong> : elle se reporte telle
     * quelle sur une campagne plantée un autre jour, là où une date ferme devrait être
     * ressaisie ligne par ligne.
     */
    @PositiveOrZero(message = "Le nombre de jours après plantation ne peut être négatif.")
    @Max(value = 1000, message = "Un cycle cultural ne dépasse pas 1000 jours.")
    private Integer daysAfterPlanting;

    @Size(max = 30)
    private String growthStage;

    @Size(max = 150)
    private String product;

    @PositiveOrZero(message = "La dose ne peut être négative.")
    private Double dose;

    @Size(max = 20)
    private String unit;

    /** Coût <strong>prévu</strong>, distinct du coût constaté sur l'intervention. */
    @PositiveOrZero(message = "Le coût estimé ne peut être négatif.")
    private BigDecimal estimatedCost;

    /** Facultatif à la création — {@code PREVUE} par défaut. */
    private PlannedOperationStatus status;

    private String note;
}
