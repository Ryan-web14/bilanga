package com.sni.bilanga.knowledge.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Règle de corrélation croisant une maladie (vision) avec une mesure
 * (capteur) pour enrichir la recommandation.
 * Ex. : mildiou tomate ET humidite_air > 80 -> conseil d'aération.
 */

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "correlation_rules")
public class CorrelationRule {

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


    @Column(name = "crop_name")
    private String cropName;

    @Column(name = "disease_code")
    private String diseaseCode;

    @Column(name = "measure_field", nullable = false)
    private String measureField;

    @Column(name = "operator", nullable = false)
    private String operator;

    @Column(name = "threshold", nullable = false)
    private Double threshold;

    @Column(name = "extra_recommendation", columnDefinition = "text", nullable = false)
    private String extraRecommendation;

    @Column(name = "priority")
    private String priority;

}