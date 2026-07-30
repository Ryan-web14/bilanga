package com.sni.bilanga.notification.model;

import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.security.admin.user.model.Users;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Ce qu'un utilisateur accepte de recevoir, et quand.
 *
 * <p><strong>Pourquoi ces réglages ne sont pas un confort.</strong> Notifier
 * tout le monde de la même façon revient à ne notifier personne : celui qui est
 * réveillé à trois heures du matin pour une alerte moyenne coupe ses
 * notifications, et n'apprendra pas non plus la critique du lendemain. Le seuil
 * personnel et les heures de silence protègent la capacité du système à être
 * entendu quand il le faut vraiment.
 *
 * <p>L'absence de préférence n'est pas un refus : les réglages globaux
 * s'appliquent alors. Exiger une saisie préalable reviendrait à ne rien envoyer
 * à personne tant que chacun n'a pas rempli un formulaire.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "notification_preference")
public class NotificationPreference {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_notif_pref_user"))
    private Users user;

    /**
     * Seuil propre à l'utilisateur, qui prime sur le seuil global.
     * Un agronome-conseil veut tout voir ; un exploitant ne veut être dérangé
     * que pour ce qui appelle une action immédiate.
     */
    @Column(name = "min_level", length = 20)
    private String minLevel;

    /**
     * Canaux retenus, séparés par des virgules : {@code "SMS,LOG"}.
     *
     * <p>Une liste courte plutôt qu'une table de liaison : trois canaux au
     * maximum, jamais interrogés autrement qu'en bloc pour un utilisateur donné.
     * Une table ajouterait une jointure sans rendre rien de plus possible.
     *
     * <p>Nul signifie « tous les canaux disponibles », et non « aucun » :
     * un champ non renseigné ne doit pas rendre le système muet.
     */
    @Column(name = "channels", length = 120)
    private String channels;

    /** Réservé aux messages d'alerte ; l'interface d'administration reste en français. */
    @Column(name = "language", nullable = false, length = 10)
    private String language;

    /**
     * Début et fin des heures de silence, en heure locale.
     *
     * <p>La plage peut enjamber minuit ({@code 22 → 6}), cas le plus courant.
     * Une alerte critique passe outre : c'est ce qui garde son sens au niveau
     * critique.
     */
    @Column(name = "quiet_from_hour")
    private Integer quietFromHour;

    @Column(name = "quiet_to_hour")
    private Integer quietToHour;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (language == null) language = "fr";
    }
}
