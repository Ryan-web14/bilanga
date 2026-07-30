package com.sni.bilanga.knowledge.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Infléchissement des seuils agronomiques pour un stade de croissance.
 *
 * Ne porte que les écarts : un champ nul signifie que le stade ne modifie pas
 * ce seuil, et la valeur générale de la culture s'applique. Décrire un stade
 * n'oblige donc pas à redéfinir l'ensemble des exigences.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "crop_stage_requirement",
        uniqueConstraints = @UniqueConstraint(name = "uq_crop_stage",
                columnNames = {"crop_name", "growth_stage"}))
public class CropStageRequirement {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    /**
     * Verrou optimiste : une modification concurrente du même enregistrement
     * échoue au lieu d'écraser silencieusement la précédente (migration V12).
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;


    @Column(name = "crop_name", nullable = false)
    private String cropName;

    @Column(name = "growth_stage", nullable = false)
    private String growthStage;

    @Column(name = "label")
    private String label;

    @Column(name = "ph_min")
    private Double phMin;

    @Column(name = "ph_max")
    private Double phMax;

    @Column(name = "hum_sol_min")
    private Double humSolMin;

    @Column(name = "hum_sol_max")
    private Double humSolMax;

    @Column(name = "temp_min")
    private Double tempMin;

    @Column(name = "temp_max")
    private Double tempMax;

    @Column(name = "azote_min")
    private Double azoteMin;

    @Column(name = "phosphore_min")
    private Double phosphoreMin;

    @Column(name = "potassium_min")
    private Double potassiumMin;

    @Column(name = "tolerance_secheresse")
    private Double toleranceSecheresse;
}
