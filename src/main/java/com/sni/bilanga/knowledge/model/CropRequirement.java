package com.sni.bilanga.knowledge.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seuils agronomiques de référence par culture.
 * Sert de base au moteur de règles pour juger les mesures capteurs.
 */

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "crop_requirement")
public class CropRequirement {

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


    @Column(name = "crop_name", nullable = false, unique = true)
    private String cropName;

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