package com.sni.bilanga.organization.model;

import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.security.admin.user.model.Users;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Exploitation : le niveau qui manquait entre l'utilisateur et la parcelle.
 *
 * <p>Une exploitation regroupe des parcelles qui peuvent être dispersées à
 * plusieurs kilomètres. Les rattacher chacune à un utilisateur ne disait rien de
 * leur appartenance commune — ni qu'un ouvrier travaille sur les cinq, ni qu'un
 * conseiller les suit ensemble.
 *
 * <p><strong>Facultative.</strong> Une parcelle sans exploitation reste
 * pleinement utilisable et se comporte exactement comme avant l'introduction de
 * ce niveau : elle appartient à son utilisateur, un point c'est tout.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "farms")
public class Farm {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Nulle pour une exploitation indépendante — cas normal, pas une lacune. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cooperative_id", foreignKey = @ForeignKey(name = "fk_farm_cooperative"))
    private Cooperative cooperative;

    /**
     * Propriétaire de référence.
     *
     * Distinct des membres : celui qui répond de l'exploitation n'est pas
     * nécessairement celui qui y travaille au quotidien.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", foreignKey = @ForeignKey(name = "fk_farm_owner"))
    private Users owner;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "location")
    private String location;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = "ACTIVE";
    }
}
