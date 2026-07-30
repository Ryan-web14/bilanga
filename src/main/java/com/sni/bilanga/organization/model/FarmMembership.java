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
 * Appartenance d'une personne à une exploitation, avec son rôle.
 *
 * <p><strong>C'est la table qui porte le cloisonnement.</strong> Le rôle n'est
 * pas une étiquette : il décide de ce que la personne voit. Un technicien accède
 * à l'état des boîtiers sans accéder aux marges — les confondre reviendrait à
 * ouvrir la comptabilité à quiconque vient changer une sonde, ce qui dans une
 * coopérative où tout le monde se connaît est un problème social avant d'être
 * technique.
 *
 * <p><strong>Une appartenance ajoute un accès ; elle n'en retire jamais.</strong>
 * Le propriétaire direct d'une parcelle garde le sien quoi qu'il arrive, même
 * si la parcelle est rattachée à une exploitation dont il n'est pas membre. Une
 * exploitation mal configurée ne peut donc enfermer personne dehors.
 *
 * <p>Un seul rôle par personne et par exploitation : deux rôles simultanés
 * rendraient indécidable le niveau d'accès applicable, et « lequel l'emporte ? »
 * n'a pas de bonne réponse.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "farm_membership")
public class FarmMembership {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_farm"))
    private Farm farm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_user"))
    private Users user;

    /** Vocabulaire de {@code MembershipRole}. */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @PrePersist
    void onCreate() {
        if (joinedAt == null) joinedAt = Instant.now();
    }
}
