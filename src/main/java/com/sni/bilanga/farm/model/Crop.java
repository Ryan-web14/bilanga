package com.sni.bilanga.farm.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "crops")
public class Crop {

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
    @JoinColumn(name = "plot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_crop_plot"))
    private Plot plot;

    @Column(name = "crop_name", nullable = false)
    private String cropName;

    @Column(name = "variety")
    private String variety;

    @Column(name = "planting_date")
    private LocalDate plantingDate;

    /**
     * Durée du cycle cultural, en jours.
     *
     * <p>C'est ce qui permet de <strong>déduire</strong> le stade courant depuis
     * la date de plantation, au lieu d'attendre qu'on le saisisse. Sans elle,
     * {@code growthStage} se périme en silence et l'ensemble du moteur
     * agronomique raisonne sur un stade faux — en croyant l'inverse.
     */
    @Column(name = "cycle_duration_days")
    private Integer cycleDurationDays;

    /** Déduite de {@code plantingDate + cycleDurationDays} lorsqu'elle n'est pas fournie. */
    @Column(name = "expected_harvest_date")
    private LocalDate expectedHarvestDate;

    /** Surface effectivement plantée, en hectares. Base du rendement et du dosage. */
    @Column(name = "planted_area")
    private Double plantedArea;

    /** Pieds à l'hectare. */
    @Column(name = "plant_density")
    private Integer plantDensity;

    /** Traçabilité : un lot défaillant se repère en croisant les parcelles. */
    @Column(name = "seed_lot", length = 60)
    private String seedLot;

    @Column(name = "growth_stage")
    private String growthStage;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Dernière modification (V28).
     *
     * <p>L'entité n'en avait aucune : impossible de savoir si une campagne avait
     * été retouchée depuis sa création. Maintenue par {@link #onUpdate()}.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ============================================================
    // Clôture (V28)
    // ============================================================

    /**
     * Date de fin <strong>réelle</strong> de la campagne.
     *
     * <p>À ne pas confondre avec {@code expectedHarvestDate}, qui est un
     * <em>objectif</em> calculé par {@code GrowthStageResolver}. Les mêler ferait
     * passer une prévision pour un constat — et c'est précisément l'écart entre les
     * deux qui dit si la campagne a tenu son calendrier.
     *
     * <p>{@code null} sur les cycles clos avant la V28 : l'information n'a jamais
     * été demandée, elle n'est pas perdue.
     */
    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    /** Vocabulaire de {@code CropClosureReason}, miroir du CHECK de la V28. */
    @Column(name = "closure_reason", length = 30)
    private String closureReason;

    @Column(name = "closure_note", columnDefinition = "text")
    private String closureNote;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by", foreignKey = @ForeignKey(name = "fk_crop_closed_by"))
    private com.sni.bilanga.security.admin.user.model.Users closedBy;

    /**
     * Bilan économique <strong>arrêté à la clôture</strong>, écrit une seule fois.
     *
     * <p><strong>Ceci amende le contrat « rien n'est stocké » de
     * {@code MarginCalculator}, il ne le viole pas.</strong> Le raisonnement de ce
     * contrat — « un total en cache diverge dès la première correction de saisie, et
     * personne ne sait plus lequel croire » — reste vrai, et c'est pourquoi trois
     * conditions le préservent : ce champ n'est jamais rafraîchi (aucune route ne le
     * permet), il est daté par {@code closedAt}, et la route vivante subsiste.
     *
     * <p>{@code GET /crops/{id}/closure} rend <strong>les deux côte à côte</strong>
     * avec leur divergence expliquée. Personne ne se demande donc lequel croire :
     * l'écart <em>devient</em> le signal d'audit.
     *
     * <p>Porte {@code scope} et {@code zoneId} en prévision du zonage de parcelle.
     * Deux clés aujourd'hui ; sans elles, des bilans de parcelle et de zone seraient
     * un jour indiscernables, et aucune migration ne rattrape une information jamais
     * écrite.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "economics_snapshot", columnDefinition = "jsonb")
    private java.util.Map<String, Object> economicsSnapshot;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}