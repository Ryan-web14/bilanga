package com.sni.bilanga.knowledge.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "disease_knowledge",
        uniqueConstraints = @UniqueConstraint(name = "uq_disease", columnNames = {"crop_name", "disease_code"}))
public class DiseaseKnowledge {

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

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "symptoms", columnDefinition = "text")
    private String symptoms;

    @Column(name = "favorable_conditions", columnDefinition = "text")
    private String favorableConditions;

    @Column(name = "treatment", columnDefinition = "text", nullable = false)
    private String treatment;

    @Column(name = "prevention", columnDefinition = "text")
    private String prevention;

    @Column(name = "priority")
    private String priority;

    /**
     * Coût indicatif du traitement, en devise locale <strong>par hectare</strong>
     * (V26, A11). Même contrat que {@link KnowledgeRule#getEstimatedCost()}.
     */
    @Column(name = "estimated_cost", precision = 14, scale = 2)
    private java.math.BigDecimal estimatedCost;

}