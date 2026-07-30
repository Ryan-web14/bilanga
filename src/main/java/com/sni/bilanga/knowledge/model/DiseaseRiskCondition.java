package com.sni.bilanga.knowledge.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Condition environnementale favorisant l'apparition d'une maladie.
 * Forme évaluable de ce que disease_knowledge.favorable_conditions
 * n'exprime qu'en prose.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "disease_risk_condition")
public class DiseaseRiskCondition {

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

    @Column(name = "disease_code", nullable = false)
    private String diseaseCode;

    @Column(name = "measure_field", nullable = false)
    private String measureField;

    @Column(name = "operator", nullable = false)
    private String operator;

    @Column(name = "threshold", nullable = false)
    private Double threshold;

    /** Borne haute, utilisée par l'opérateur BETWEEN. */
    @Column(name = "threshold_max")
    private Double thresholdMax;

    /** Poids de la condition dans le calcul du risque. */
    @Column(name = "weight", nullable = false)
    private Double weight;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "active", nullable = false)
    private Boolean active;
}