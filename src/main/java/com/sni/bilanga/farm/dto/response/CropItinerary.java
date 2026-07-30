package com.sni.bilanga.farm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * L'itinéraire technique d'une campagne : ce qui était prévu, et ce qui a suivi.
 *
 * <p><strong>Le troisième terme.</strong> Le système savait ce qui a été fait
 * ({@code interventions}) et ce qu'il conseille ({@code recommendations}). Il ne savait
 * rien de ce qui était <em>prévu</em> — et sans ce terme, une opération oubliée est
 * indiscernable d'une opération jamais planifiée.
 *
 * <p><strong>Les rapprochements sont recalculés à chaque appel.</strong> Seules les
 * confirmations humaines sont écrites en base ({@code matchConfirmed: true}). Un mauvais
 * appariement persisté se propage au bilan et au clonage, et doit être défait à la main ;
 * un mauvais appariement recalculé disparaît dès que la donnée s'améliore.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CropItinerary {

    private Long cropId;
    private Long plotId;
    private String plotName;
    private String cropName;

    /** Sert à résoudre les opérations datées en {@code J+n}. */
    private LocalDate plantingDate;

    /** Triées sur la date retenue ; les non datables en fin de liste. */
    private List<PlannedOperationResponse> operations;

    private Integer operationCount;

    /** Opérations rapprochées d'une intervention réelle, confirmées ou inférées. */
    private Integer matchedCount;

    /** Date retenue passée, aucun rapprochement, statut encore {@code PREVUE}. */
    private Integer lateCount;

    /**
     * Part des opérations prévues qui ont trouvé une intervention, en pourcentage.
     *
     * <p>{@code null} sur un itinéraire vide : {@code 0 %} laisserait croire que rien
     * n'a été fait, alors que rien n'a été planifié.
     */
    private Double completionRate;

    // ------------------------------------------------------------
    // Économie prévisionnelle
    // ------------------------------------------------------------

    /**
     * Somme des coûts <strong>prévus</strong>.
     *
     * <p>C'est ce que l'itinéraire apporte de neuf au bilan : jusqu'ici, le coût d'une
     * campagne ne se connaissait qu'après la récolte.
     */
    private BigDecimal totalEstimatedCost;

    /** Somme des coûts <strong>constatés</strong> sur les interventions rapprochées. */
    private BigDecimal totalActualCost;

    /**
     * {@link #totalActualCost} − {@link #totalEstimatedCost}. Positif = dépassement.
     *
     * <p>{@code null} si l'un des deux côtés est vide — un écart calculé contre zéro
     * ferait passer une absence de saisie pour une économie.
     */
    private BigDecimal costVariance;

    /** Résumé rédigé, prêt à afficher. */
    private String summary;

    /**
     * Réserve, <strong>toujours renseignée</strong>.
     *
     * <p>Les rapprochements automatiques sont des inférences : rien n'établit qu'une
     * fertilisation du 14 mai est celle qui était prévue le 12.
     */
    private String limitation;

    /** Ce qui empêche de lire l'itinéraire sans réserve, expliqué ligne par ligne. */
    private List<String> missingData;

    private Instant generatedAt;
}
