package com.sni.bilanga.organization.dto.request;

import com.sni.bilanga.enums.PlotStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Création ou mise à jour d'une exploitation.
 *
 * <p>{@code cooperativeId} est facultatif : une exploitation indépendante est le
 * cas normal, pas une configuration incomplète.
 */
@Data
public class FarmRequest {

    @NotBlank(message = "Le nom de l'exploitation est obligatoire")
    @Size(max = 150, message = "Le nom ne peut dépasser 150 caractères")
    private String name;

    /** Coopérative de rattachement ; absent pour une exploitation indépendante. */
    private Long cooperativeId;

    /**
     * Propriétaire de référence.
     *
     * Distinct des membres : celui qui répond de l'exploitation n'est pas
     * nécessairement celui qui y travaille. Il est ajouté comme
     * {@code PROPRIETAIRE} à la création, pour qu'une exploitation ne naisse
     * jamais sans personne qui puisse la consulter.
     */
    private Long ownerUserId;

    @Size(max = 255, message = "La localisation ne peut dépasser 255 caractères")
    private String location;

    @Size(max = 30, message = "Le téléphone ne peut dépasser 30 caractères")
    private String contactPhone;

    private PlotStatus status;
}
