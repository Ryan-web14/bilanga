package com.sni.bilanga.farm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Une opération prévue, avec l'état de son rapprochement.
 *
 * <p>Trois champs sont <strong>calculés à la lecture</strong> et n'existent nulle part
 * en base : {@link #resolvedDate}, {@link #late} et l'ensemble du bloc de rapprochement
 * quand il est automatique. Les persister les rendrait faux — le retard dès le
 * lendemain, la date résolue dès qu'on corrige la date de plantation.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PlannedOperationResponse {

    private Long id;
    private Long cropId;

    private String type;
    private String typeLabel;
    private String label;

    /** Date ferme telle que saisie, ou {@code null} si l'opération est en {@code J+n}. */
    private LocalDate plannedOn;

    private Integer daysAfterPlanting;

    /**
     * Date effectivement retenue : {@link #plannedOn} si elle existe, sinon
     * {@code plantingDate + daysAfterPlanting}.
     *
     * <p>{@code null} si l'opération est en {@code J+n} et que la campagne n'a pas de
     * date de plantation — cas signalé dans {@code missingData} de l'itinéraire, jamais
     * silencieux.
     */
    private LocalDate resolvedDate;

    private String growthStage;
    private String product;
    private Double dose;
    private String unit;

    /** Formaté, prêt à afficher : {@code "12,50 kg/ha"}. */
    private String dosage;

    private BigDecimal estimatedCost;

    private String status;
    private String statusLabel;

    /**
     * Vrai si la date retenue est passée et qu'aucune intervention n'a été rapprochée.
     *
     * <p><strong>Calculé, jamais stocké.</strong> Le projet n'a ni ordonnanceur ni tâche
     * de fond : un statut {@code EN_RETARD} persisté serait faux dès le lendemain de son
     * écriture.
     *
     * <p>{@code null} quand la question ne se pose pas — opération non datable, ou
     * statut déjà tranché ({@code ABANDONNEE}, {@code REALISEE}).
     */
    private Boolean late;

    /** Jours de retard, si {@link #late}. Un nombre, non une chaîne. */
    private Integer lateByDays;

    // ------------------------------------------------------------
    // Rapprochement
    // ------------------------------------------------------------

    /** Intervention rapprochée, confirmée ou inférée. */
    private Long interventionId;

    private Instant interventionPerformedAt;

    /** Coût <strong>réellement</strong> constaté, à confronter à {@link #estimatedCost}. */
    private BigDecimal interventionCost;

    private String matchConfidence;
    private String matchConfidenceLabel;

    /**
     * Vrai si le rapprochement a été <strong>confirmé par un humain</strong> et écrit en
     * base ; faux s'il est inféré et recalculé à chaque lecture.
     *
     * <p>À afficher différemment : l'un est un fait, l'autre une hypothèse du système.
     */
    private Boolean matchConfirmed;

    /**
     * Écart <strong>signé</strong> entre la date prévue et la date constatée. Négatif si
     * l'opération a été faite en avance.
     */
    private Integer matchGapDays;

    private Instant matchedAt;

    /** Formulation française du rapprochement, ou de son absence. Prête à afficher. */
    private String matchStatement;

    private Instant createdAt;
    private Instant updatedAt;
}
