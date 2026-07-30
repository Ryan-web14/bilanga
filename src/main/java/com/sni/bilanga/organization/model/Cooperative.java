package com.sni.bilanga.organization.model;

import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Coopérative regroupant plusieurs exploitations.
 *
 * <p>Forme dominante de l'agriculture congolaise, et le niveau que le modèle
 * « une parcelle, un utilisateur » ne pouvait pas représenter : un président de
 * coopérative n'avait aucun moyen de voir l'état des quarante exploitations dont
 * il répond.
 *
 * <p><strong>Facultative à tous égards.</strong> Une exploitation indépendante
 * n'en a aucune, et le système n'en exige jamais une. Aucun chemin de code
 * n'échoue faute de coopérative — c'est le niveau le plus haut et le plus
 * optionnel de la hiérarchie.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "cooperatives")
public class Cooperative {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Référence lisible ({@code COOP-2026-000003}), sur le modèle du code de parcelle. */
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
