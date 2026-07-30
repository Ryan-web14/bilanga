package com.sni.bilanga.intervention.dto.request;

import com.sni.bilanga.enums.InterventionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Déclaration d'une action menée au champ.
 *
 * <p>Seuls la parcelle et le type sont obligatoires : une saisie faite le soir,
 * de mémoire, ne doit pas être refusée parce que l'exploitant ne se souvient
 * plus du dosage exact. Une intervention consignée approximativement vaut
 * infiniment mieux qu'une intervention non consignée.
 */
@Data
public class InterventionRequest {

    @NotNull(message = "La parcelle est obligatoire")
    private Long plotId;

    /**
     * Culture concernée. Déduite de la culture en cours sur la parcelle si elle
     * n'est pas transmise — l'exploitant n'a pas à répéter ce que le système sait.
     */
    private Long cropId;

    /**
     * Conseil suivi, s'il y en a un.
     *
     * Le renseigner bascule automatiquement la recommandation en
     * {@code APPLIQUEE} : c'est ce qui ferme la boucle sans double saisie.
     */
    private Long recommendationId;

    @NotNull(message = "Le type d'intervention est obligatoire")
    private InterventionType type;

    @Size(max = 150, message = "Le nom du produit ne peut dépasser 150 caractères")
    private String product;

    @PositiveOrZero(message = "La dose ne peut être négative")
    private Double dose;

    @Size(max = 20, message = "L'unité ne peut dépasser 20 caractères")
    private String unit;

    @DecimalMin(value = "0", message = "Le coût ne peut être négatif")
    private BigDecimal cost;

    /**
     * Date d'exécution. Par défaut l'instant de la saisie — mais une saisie
     * différée doit pouvoir porter la vraie date, sans quoi l'analyse d'effet
     * comparerait les mauvaises fenêtres.
     */
    @PastOrPresent(message = "La date d'exécution ne peut être dans le futur")
    private Instant performedAt;

    /** Auteur. Déduit de l'utilisateur authentifié s'il n'est pas transmis. */
    private Long performedById;

    @Size(max = 300, message = "La note météo ne peut dépasser 300 caractères")
    private String weatherNote;

    private String note;
}
