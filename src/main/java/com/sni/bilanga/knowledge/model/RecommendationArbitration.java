package com.sni.bilanga.knowledge.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Règle de conciliation entre deux domaines de conseil qui se contrarient
 * à la lecture sans se contredire sur le fond.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "recommendation_arbitration")
public class RecommendationArbitration {

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

    @Column(name = "category_a", nullable = false)
    private String categoryA;

    @Column(name = "category_b", nullable = false)
    private String categoryB;

    /** Conseil de synthèse, indiquant comment concilier les deux domaines. */
    @Column(name = "synthesis", columnDefinition = "text", nullable = false)
    private String synthesis;

    @Column(name = "priority", nullable = false)
    private String priority;

    @Column(name = "active", nullable = false)
    private Boolean active;
}