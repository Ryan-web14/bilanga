package com.sni.bilanga.security.admin.user.model;


import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.security.admin.role.model.RoleUser;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class Users {

    /**
     * Objet et non primitif, comme toutes les autres entités.
     *
     * En {@code long}, une instance neuve porte l'identifiant 0 au lieu de
     * « pas encore d'identifiant » : Hibernate ne peut plus distinguer une
     * entité transitoire d'une entité persistée, et le générateur Snowflake
     * n'a aucun moyen de signaler qu'il n'a pas encore attribué de valeur.
     * {@code Users} était la seule entité du projet dans ce cas.
     */
    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "firstname", length = 255)
    private String firstname;

    @Column(name = "lastname", length = 255)
    private String lastname;

    /**
     * Numéro de téléphone, destinataire des notifications SMS.
     *
     * <p>Aucune table n'en portait jusqu'ici : le seul canal implémenté écrivait
     * dans les journaux et n'avait donc besoin de personne, ce qui a masqué le
     * manque. Or le SMS est le canal qui atteint réellement l'exploitant —
     * il fonctionne sur téléphone simple, sans forfait données, avec une
     * couverture bien supérieure à celle de l'internet mobile.
     *
     * <p>Non unique volontairement : un chef d'exploitation et son ouvrier
     * peuvent partager le seul téléphone du village.
     */
    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "deleted")
    private boolean deleted = Boolean.FALSE;

    @Column(name = "is_account_expired")
    private Boolean isAccountExpired;

    @Column(name = "is_account_locked")
    private Boolean isAccountLocked;

    @Column(name = "is_account_enabled")
    private Boolean isAccountEnabled;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts;

    @OneToMany(mappedBy = "users", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<RoleUser> roleUsers;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
}
