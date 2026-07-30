package com.sni.bilanga.farm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PlotResponse {

    private Long id;

    /** Référence lisible attribuée à la création : {@code PARC-2026-000014}. */
    private String plotCode;

    private String name;
    private String location;

    private Double latitude;
    private Double longitude;
    private Double altitude;

    /** Vrai si la parcelle porte des coordonnées exploitables (météo, voisinage). */
    private Boolean geolocated;

    private String soilType;

    /** Libellé lisible du type de sol, pour éviter que chaque client le retraduise. */
    private String soilTypeLabel;

    private String irrigationType;
    private String irrigationTypeLabel;

    /**
     * Faux lorsqu'on sait que la parcelle ne peut pas être irriguée sur commande.
     * Nul si le moyen d'irrigation n'a pas été renseigné — l'ignorance n'est pas
     * une réponse négative.
     */
    private Boolean waterOnDemand;

    private Double area;
    private String status;
    private String statusLabel;

    private Long userId;

    /** Exploitation de rattachement ; nulle pour une parcelle indépendante. */
    private Long farmId;
    private String farmName;

    /** Coopérative de l'exploitation, quand il y en a une. */
    private Long cooperativeId;
    private String cooperativeName;

    private Instant createdAt;
    private Instant updatedAt;
}
