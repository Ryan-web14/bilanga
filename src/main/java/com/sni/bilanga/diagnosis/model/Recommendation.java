package com.sni.bilanga.diagnosis.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Recommandation générée à partir d'un diagnostic.
 * recommendation_type = BASE | CORRELATION.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "recommendations")
public class Recommendation {

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


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagnostic_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reco_diag"))
    private Diagnostic diagnostic;

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "recommendation_type")
    private String recommendationType;

    @Column(name = "priority")
    private String priority;

    @Column(name = "status")
    private String status;

    @Column(name = "source_rule_id")
    private Long sourceRuleId;

    @Column(name = "measure_field")
    private String measureField;

    @Column(name = "observed_value")
    private Double observedValue;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    /**
     * Coût estimé de la mise en œuvre du conseil.
     *
     * Un conseil chiffré se compare à son bénéfice ; c'est ce qui rendra
     * possible l'analyse de marge. Nul tant que la règle qui l'a produit ne
     * porte pas d'estimation.
     */
    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private java.math.BigDecimal estimatedCost;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Date de la réponse de l'exploitant ; nulle tant que le conseil est à traiter. */
    @Column(name = "feedback_at")
    private Instant feedbackAt;

    /**
     * Motif de la réponse. C'est sur un conseil écarté qu'il importe : c'est là
     * que se trouve ce qui permettra d'amender la règle qui l'a produit.
     */
    @Column(name = "feedback_note", length = 500)
    private String feedbackNote;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}