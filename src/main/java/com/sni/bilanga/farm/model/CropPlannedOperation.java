package com.sni.bilanga.farm.model;

import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.intervention.model.Intervention;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Une opération <strong>prévue</strong> sur une campagne : l'itinéraire technique.
 *
 * <p><strong>Le troisième terme.</strong> Le système enregistrait ce qui a été fait
 * ({@link Intervention}) et ce qu'il conseille ({@code Recommendation}). Il ne savait
 * rien de ce qui était <em>prévu</em> — or c'est ce terme-là qui rend les deux autres
 * lisibles. Sans lui, une opération oubliée est indiscernable d'une opération jamais
 * planifiée, et le coût prévisionnel d'une campagne ne se calcule qu'après la récolte.
 *
 * <p><strong>Deux manières de dater, et c'est délibéré.</strong> {@link #plannedOn} est
 * une date ferme ; {@link #daysAfterPlanting} est une position dans le cycle. La
 * seconde est celle d'un itinéraire réutilisable : elle survit au clonage vers une
 * campagne plantée un autre jour, là où une date ferme devrait être ressaisie ligne par
 * ligne. La résolution de {@code J+n} en date réelle se fait <strong>à la lecture</strong>
 * — un calcul persisté deviendrait faux dès qu'on corrige la date de plantation.
 *
 * <p><strong>{@link #intervention} ne porte que les rapprochements confirmés.</strong>
 * Les rapprochements automatiques sont recalculés à chaque lecture par
 * {@code ItineraryMatcher} et ne sont jamais écrits ici : un mauvais appariement qui se
 * persiste devra être corrigé à la main, là où un mauvais appariement qui se recalcule
 * disparaît dès que la donnée s'améliore.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "crop_planned_operations")
public class CropPlannedOperation {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_planned_op_crop"))
    private Crop crop;

    /**
     * Vocabulaire de {@code InterventionType}, et non un vocabulaire propre.
     *
     * <p>Le rapprochement prévu ↔ réalisé se fait sur {@code (crop, type)} : deux
     * vocabulaires distincts le rendraient impossible, et rien ne permettrait de s'en
     * apercevoir — les listes seraient simplement toujours vides.
     */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** Intitulé libre : « deuxième apport d'azote », « traitement préventif mildiou ». */
    @Column(name = "label", length = 150)
    private String label;

    /** Date ferme, si l'itinéraire en porte une. */
    @Column(name = "planned_on")
    private LocalDate plannedOn;

    /** Position dans le cycle. Résolue en date à la lecture, jamais persistée résolue. */
    @Column(name = "days_after_planting")
    private Integer daysAfterPlanting;

    /**
     * Stade visé, indicatif.
     *
     * <p>Non contraint au vocabulaire des stades connus : un itinéraire peut viser un
     * stade qu'aucune table de seuils ne décrit, et le refuser n'apporterait rien.
     */
    @Column(name = "growth_stage", length = 30)
    private String growthStage;

    @Column(name = "product", length = 150)
    private String product;

    @Column(name = "dose")
    private Double dose;

    @Column(name = "unit", length = 20)
    private String unit;

    /**
     * Coût <strong>prévu</strong>, à ne pas confondre avec {@code Intervention.cost}
     * qui est constaté. Leur écart est précisément ce qu'on veut pouvoir lire.
     */
    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    /** Rapprochement <strong>confirmé</strong> uniquement — voir la javadoc de classe. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_id",
            foreignKey = @ForeignKey(name = "fk_planned_op_intervention"))
    private Intervention intervention;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(name = "match_confidence", length = 20)
    private String matchConfidence;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = com.sni.bilanga.enums.PlannedOperationStatus.PREVUE.name();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
