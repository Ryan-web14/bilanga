package com.sni.bilanga.organization.dto.request;

import com.sni.bilanga.enums.PlotStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Création ou mise à jour d'une coopérative.
 *
 * <p>Le seul champ obligatoire est le nom : une coopérative se déclare avant
 * d'avoir toutes ses informations, et exiger davantage retarderait la saisie
 * sans rien garantir.
 */
@Data
public class CooperativeRequest {

    @NotBlank(message = "Le nom de la coopérative est obligatoire")
    @Size(max = 150, message = "Le nom ne peut dépasser 150 caractères")
    private String name;

    @Size(max = 255, message = "La localisation ne peut dépasser 255 caractères")
    private String location;

    @Size(max = 30, message = "Le téléphone ne peut dépasser 30 caractères")
    private String contactPhone;

    /** Réutilise le vocabulaire ACTIVE / ARCHIVEE des parcelles. */
    private PlotStatus status;
}
