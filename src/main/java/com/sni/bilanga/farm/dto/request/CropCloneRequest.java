package com.sni.bilanga.farm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Relancer une campagne sur le modèle d'une précédente.
 *
 * <p><strong>Le geste que cela remplace.</strong> Ressaisir une campagne identique à
 * celle de l'an dernier — même culture, même variété, même densité, même itinéraire —
 * demandait de retrouver l'ancienne fiche et de recopier une dizaine de champs, plus
 * autant de lignes d'itinéraire. Chaque recopie est une occasion de se tromper, et une
 * densité mal reportée fausse le rendement à l'hectare pour toute la campagne.
 *
 * <p><strong>Seule {@code plantingDate} est obligatoire.</strong> Tout le reste est
 * repris de la source, et chaque champ renseigné ici la surcharge.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CropCloneRequest {

    /**
     * Parcelle d'accueil. Omise, la campagne est relancée sur <strong>la même</strong>.
     *
     * <p>La renseigner permet de reporter un itinéraire éprouvé d'une parcelle à une
     * autre — le cas le plus utile après la reconduction à l'identique.
     */
    private Long plotId;

    @NotNull(message = "La date de plantation de la nouvelle campagne est obligatoire.")
    private LocalDate plantingDate;

    /** Surcharge la variété de la source. */
    @Size(max = 100)
    private String variety;

    /**
     * Lot de semence de la <strong>nouvelle</strong> campagne.
     *
     * <p>Jamais repris de la source : un lot est consommé, et le reporter serait un
     * mensonge de traçabilité — exactement le champ dont on a besoin le jour où l'on
     * cherche l'origine d'un problème de levée.
     */
    @Size(max = 100)
    private String seedLot;

    @Positive(message = "La surface plantée doit être positive.")
    private Double plantedArea;

    @PositiveOrZero(message = "La densité ne peut être négative.")
    private Integer plantDensity;

    @Positive(message = "La durée du cycle doit être positive.")
    private Integer cycleDurationDays;

    /**
     * Reporter l'itinéraire technique de la source, décalé sur la nouvelle plantation.
     *
     * <p>Vrai par défaut : c'est la raison d'être du clonage. Les opérations datées en
     * {@code J+n} se reportent telles quelles ; les dates fermes sont décalées du même
     * nombre de jours que la plantation.
     */
    @Builder.Default
    private Boolean copyItinerary = Boolean.TRUE;
}
