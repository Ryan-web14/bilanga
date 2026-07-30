package com.sni.bilanga.intervention.model;

import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.diagnosis.model.Recommendation;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.security.admin.user.model.Users;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Action menée au champ : ce que l'exploitant a réellement fait.
 *
 * <p><strong>Le chaînon qui manquait.</strong> Le système conseille et ne sait
 * pas ce qui a été fait. {@code Observation} ne porte qu'une note libre ;
 * {@code Recommendation.status = APPLIQUEE} dit qu'un conseil a été suivi, sans
 * dire comment, quand, à quelle dose ni pour quel coût. La chaîne s'arrêtait au
 * conseil — et rien ne permettait de dire si le système sert à quelque chose.
 *
 * <p><strong>Pourquoi {@link #recommendation} est facultative.</strong> Les
 * exploitants agissent aussi de leur propre chef, et ce sont précisément ces
 * actions-là qui expliquent des évolutions que le système ne comprendrait pas
 * autrement : une irrigation non conseillée fait remonter l'humidité, et sans
 * cette trace le moteur conclurait à une pluie qui n'a pas eu lieu.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "interventions")
public class Intervention {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_intervention_plot"))
    private Plot plot;

    /** Absente lors d'un travail d'inter-campagne, sur une parcelle sans plantation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id", foreignKey = @ForeignKey(name = "fk_intervention_crop"))
    private Crop crop;

    /**
     * Conseil à l'origine de l'action, quand il y en a un.
     *
     * Le renseigner bascule la recommandation en {@code APPLIQUEE} : c'est ce
     * qui ferme la boucle sans imposer une double saisie.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id",
            foreignKey = @ForeignKey(name = "fk_intervention_recommendation"))
    private Recommendation recommendation;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** Produit employé : engrais, fongicide, semence. Base de la traçabilité des intrants. */
    @Column(name = "product", length = 150)
    private String product;

    @Column(name = "dose")
    private Double dose;

    @Column(name = "unit", length = 20)
    private String unit;

    /** Coût réel, base de l'analyse de marge. */
    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", foreignKey = @ForeignKey(name = "fk_intervention_user"))
    private Users performedBy;

    /**
     * Conditions au moment de l'intervention.
     *
     * Traiter juste avant une pluie annonce un lessivage : sans cette note,
     * l'absence d'effet resterait inexpliquée et l'on conclurait à tort à
     * l'inefficacité du produit.
     */
    @Column(name = "weather_note", length = 300)
    private String weatherNote;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (performedAt == null) performedAt = createdAt;
    }
}
