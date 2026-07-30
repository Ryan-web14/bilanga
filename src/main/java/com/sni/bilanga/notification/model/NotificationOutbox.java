package com.sni.bilanga.notification.model;

import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Intention d'envoi, écrite dans la transaction qui lève l'alerte.
 *
 * Notifier directement depuis le service d'alerte exposerait à perdre l'envoi
 * si le canal est momentanément indisponible — et, pire, à envoyer une
 * notification pour une alerte dont la transaction finirait par échouer.
 * Enregistrer l'intention puis la traiter règle les deux : rien n'est envoyé
 * pour une alerte qui n'existe pas, rien n'est perdu si le canal ne répond pas.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "plot_id")
    private Long plotId;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    @Column(name = "recipient")
    private String recipient;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "level", length = 20)
    private String level;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    /**
     * Empreinte de regroupement : {@code <parcelle>:<niveau>:<tranche horaire>}.
     *
     * <p>Cinq alertes en dix minutes doivent faire un message, pas cinq. Sans
     * cela, une parcelle qui bascule d'un coup vide le crédit SMS de
     * l'exploitation et sature le téléphone de l'exploitant — qui coupera ses
     * notifications, y compris celles qui comptent.
     */
    @Column(name = "group_key", length = 120)
    private String groupKey;

    /**
     * Report jusqu'à la fin des heures de silence du destinataire.
     *
     * Nul signifie « à envoyer sans délai ». Une alerte critique n'est jamais
     * reportée : c'est ce qui garde son sens au niveau critique.
     */
    @Column(name = "deferred_until")
    private Instant deferredUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (attempts == null) attempts = 0;
        if (status == null) status = "EN_ATTENTE";
    }
}
