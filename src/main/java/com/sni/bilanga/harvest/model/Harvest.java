package com.sni.bilanga.harvest.model;

import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Ce qui a été récolté, et à quel prix.
 *
 * <p><strong>Ce que cela ferme.</strong> Le système dit ce qu'il faut faire ; il
 * ne disait pas si cela avait rapporté. Avec les charges portées par les
 * interventions et la surface plantée portée par la culture, la récolte complète
 * le triangle : produit brut, charges, marge. C'est ce qui transforme un
 * assistant technique en outil de gestion — et, pour le mémoire, ce qui permet
 * de <em>quantifier</em> l'apport de la plateforme au lieu de le postuler.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "harvests")
public class Harvest {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_harvest_plot"))
    private Plot plot;

    /**
     * Obligatoire, contrairement à une intervention : une récolte qui ne se
     * rattache à aucune campagne n'entre dans aucun calcul de marge, et n'a donc
     * pas d'usage.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_harvest_crop"))
    private Crop crop;

    @Column(name = "quantity")
    private Double quantity;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "quality", length = 20)
    private String quality;

    @Column(name = "harvested_at", nullable = false)
    private LocalDate harvestedAt;

    /**
     * Prix unitaire, et non montant total.
     *
     * Le total se recalcule ; un prix unitaire perdu ne se retrouve pas. Il
     * permet en outre de comparer deux campagnes de volumes différents, ce qu'un
     * montant global interdirait.
     */
    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (harvestedAt == null) harvestedAt = LocalDate.now();
        if (currency == null) currency = "XAF";
    }
}
