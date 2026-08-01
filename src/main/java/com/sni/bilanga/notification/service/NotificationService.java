package com.sni.bilanga.notification.service;

import com.sni.bilanga.enums.NotificationLanguage;
import com.sni.bilanga.notification.service.support.NotificationMessages;
import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.model.Alert;
import com.sni.bilanga.enums.AlertLevel;
import com.sni.bilanga.enums.NotificationStatus;
import com.sni.bilanga.notification.channel.LogNotificationChannel;
import com.sni.bilanga.notification.channel.NotificationChannel;
import com.sni.bilanga.notification.model.NotificationOutbox;
import com.sni.bilanga.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Achemine les alertes vers l'extérieur.
 *
 * <p>Le déroulé tient en deux temps, imposés par l'absence d'infrastructure
 * asynchrone dans le projet :
 *
 * <ol>
 *   <li>l'intention d'envoi est écrite <em>dans la transaction de l'alerte</em> —
 *       ainsi rien n'est notifié pour une alerte dont la transaction échouerait ;</li>
 *   <li>la tentative d'envoi a lieu juste après, hors de cette transaction. Un
 *       canal indisponible laisse la ligne en attente au lieu de faire échouer
 *       le diagnostic — perdre un diagnostic pour un serveur de courriel muet
 *       serait absurde.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationOutboxRepository outboxRepository;
    private final List<NotificationChannel> channels;
    private final RecipientResolver recipientResolver;
    private final BilangaProperties.Notification notificationConfig;
    private final NotificationMessages messages;





    /**
     * Enregistre l'intention de notifier une alerte.
     *
     * Appelée depuis la transaction qui vient de lever l'alerte ; ne tente
     * aucun envoi.
     */
    @Transactional
    public void enqueue(Alert alert) {
        if (!notificationConfig.isEnabled()) {
            return;
        }

        // À qui l'on s'adresse, et ce que cette personne accepte de recevoir.
        // Le service enfilait jusqu'ici une ligne pour tout canal disponible,
        // sans destinataire : sans importance tant que le seul canal écrivait
        // dans les journaux, bloquant dès qu'un canal réel apparaît.
        RecipientResolver.Target target = recipientResolver.resolve(alert);

        if (!recipientResolver.warrants(target, alert)) {
            return;
        }

        Instant deferredUntil = recipientResolver.deferralFor(target, alert);
        String groupKey = groupKeyFor(alert);

        // La langue du destinataire, et non celle du serveur. Les notifications
        // sont les seuls messages que l'application adresse à quelqu'un qui n'a
        // pas choisi de la consulter, sur un téléphone simple, au champ : c'est le
        // seul endroit où la langue décide si le message est lu ou ignoré.
        NotificationLanguage language = languageOf(target);

        for (NotificationChannel channel : channels) {
            if (!channel.isAvailable() || !recipientResolver.accepts(target, channel.name())) {
                continue;
            }
            if (outboxRepository.existsByAlertIdAndChannel(alert.getId(), channel.name())) {
                continue;
            }

            String recipient = recipientResolver.addressFor(target, channel.name());
            if (recipient == null && requiresRecipient(channel)) {
                // Enfiler sans adresse produirait une ligne qui échoue à chaque
                // reprise sans jamais aboutir. Mieux vaut le dire une fois dans
                // les journaux que le répéter cinq fois en base.
                log.warn("Alerte {} non notifiée sur {} : aucune adresse pour l'utilisateur {}.",
                        alert.getId(), channel.name(),
                        target.user() == null ? "(aucun)" : target.user().getId());
                continue;
            }

            // Regroupement : une ligne encore en attente sur la même empreinte
            // est complétée au lieu d'être doublée. Cinq alertes en dix minutes
            // font un message, pas cinq — sans quoi l'exploitant coupe ses
            // notifications et n'apprendra pas non plus la suivante.
            Optional<NotificationOutbox> pending = groupKey == null
                    ? Optional.empty()
                    : outboxRepository.findFirstByGroupKeyAndChannelAndStatus(
                            groupKey, channel.name(), NotificationStatus.EN_ATTENTE.name());

            if (pending.isPresent()) {
                appendTo(pending.get(), alert, language);
                continue;
            }

            outboxRepository.save(NotificationOutbox.builder()
                    .alertId(alert.getId())
                    .plotId(alert.getPlot() == null ? null : alert.getPlot().getId())
                    .channel(channel.name())
                    .recipient(recipient)
                    .subject(messages.subjectFor(alert, language))
                    .body(messages.bodyFor(alert, language))
                    .level(alert.getLevel())
                    .status(NotificationStatus.EN_ATTENTE.name())
                    .groupKey(groupKey)
                    .deferredUntil(deferredUntil)
                    .attempts(0)
                    .build());
        }

        dispatchAfterCommit();
    }

    /**
     * Réunit une alerte supplémentaire dans un envoi déjà en attente.
     *
     * <p>Le corps est augmenté plutôt que remplacé : les deux situations sont
     * réelles, et n'en transmettre qu'une reviendrait à taire l'autre. Le sujet,
     * lui, devient un décompte — « 3 situations sur la parcelle X » —, un
     * en-tête ayant plus de valeur qu'une répétition.
     */
    private void appendTo(NotificationOutbox notification, Alert alert,
                          NotificationLanguage language) {
        String addition = alert.getMessage() == null ? "" : alert.getMessage();
        if (addition.isBlank()) {
            return;
        }

        notification.setBody(notification.getBody() + System.lineSeparator() + "· " + addition);

        // Le niveau de l'envoi suit le plus élevé des regroupées : réunir une
        // alerte critique dans un message annoncé « ELEVEE » masquerait
        // précisément ce qui appelle une action immédiate.
        AlertLevel existing = AlertLevel.from(notification.getLevel());
        AlertLevel incoming = AlertLevel.from(alert.getLevel());
        if (existing == null || (incoming != null && incoming.isAtLeast(existing))) {
            notification.setLevel(alert.getLevel());
        }

        // Le sujet regroupé suit la même langue que le message d'origine : mêler
        // deux langues dans un envoi que le destinataire lira comme un seul message
        // serait plus déroutant que de n'en traduire aucune.
        notification.setSubject(messages.groupedSubjectFor(
                alert, language, countLines(notification.getBody())));

        outboxRepository.save(notification);
    }

    private int countLines(String body) {
        return body == null ? 1 : body.split("\\R").length;
    }

    /**
     * Empreinte de regroupement : parcelle, niveau, tranche horaire.
     *
     * <p>Le niveau en fait partie délibérément : réunir une alerte moyenne et
     * une alerte critique dans un même message ferait passer la seconde pour
     * une ligne parmi d'autres.
     */
    private String groupKeyFor(Alert alert) {
        if (alert.getPlot() == null) {
            return null;
        }
        return alert.getPlot().getId() + ":" + alert.getLevel() + ":"
               + recipientResolver.groupingSlot(Instant.now());
    }

    /**
     * Le canal de repli écrit dans les journaux : il n'a pas de destinataire à
     * connaître, et exiger une adresse le rendrait muet précisément dans le cas
     * où il sert — quand aucun canal réel n'est configuré.
     */
    private boolean requiresRecipient(NotificationChannel channel) {
        return !LogNotificationChannel.NAME.equals(channel.name());
    }

    /**
     * Programme la tentative d'envoi pour l'instant qui suit la validation de la
     * transaction en cours.
     *
     * Envoyer immédiatement reviendrait à notifier une alerte que la transaction
     * pourrait encore annuler — l'exploitant se déplacerait pour une situation
     * qui n'a jamais été enregistrée. C'est le seul point d'accroche disponible
     * ici : le projet n'a ni ordonnanceur ni exécution asynchrone.
     */
    private void dispatchAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchPending(notificationConfig.getDispatchBatchSize());
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dispatchPending(notificationConfig.getDispatchBatchSize());
                } catch (Exception e) {
                    // L'alerte est enregistrée et l'envoi reste en attente :
                    // échouer ici ne doit rien remettre en cause.
                    log.warn("Acheminement des notifications différé : {}", e.getMessage());
                }
            }
        });
    }

    /**
     * Traite la file en attente.
     *
     * Invoquée après l'ingestion et exposée à l'administration. Le lot est borné :
     * une reprise ne doit pas monopoliser le fil qui l'a déclenchée.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatchPending(int batchSize) {
        if (!notificationConfig.isEnabled()) {
            return 0;
        }

        Set<String> retryable = Set.of(
                NotificationStatus.EN_ATTENTE.name(), NotificationStatus.ECHOUEE.name());

        // Les envois reportés pour heures de silence sont écartés jusqu'à leur
        // terme : c'est ce report qui évite de réveiller quelqu'un à trois
        // heures du matin pour une situation qui pouvait attendre l'aube.
        List<NotificationOutbox> pending = outboxRepository
                .findDispatchable(retryable, Instant.now(), PageRequest.of(0, batchSize));

        int sent = 0;
        for (NotificationOutbox notification : pending) {
            if (attempt(notification)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean attempt(NotificationOutbox notification) {
        NotificationChannel channel = channels.stream()
                .filter(c -> c.name().equals(notification.getChannel()))
                .findFirst()
                .orElse(null);

        notification.setAttempts(notification.getAttempts() + 1);
        notification.setLastAttemptAt(Instant.now());

        if (channel == null || !channel.isAvailable()) {
            // Canal disparu ou non configuré : compter des échecs indéfiniment
            // n'apporterait rien.
            notification.setStatus(NotificationStatus.ABANDONNEE.name());
            notification.setLastError("Canal " + notification.getChannel() + " indisponible.");
            outboxRepository.save(notification);
            return false;
        }

        try {
            channel.send(notification);
            notification.setStatus(NotificationStatus.ENVOYEE.name());
            notification.setSentAt(Instant.now());
            notification.setLastError(null);
            outboxRepository.save(notification);
            return true;

        } catch (Exception e) {
            boolean exhausted = notification.getAttempts() >= notificationConfig.getMaxAttempts();
            notification.setStatus(exhausted
                    ? NotificationStatus.ABANDONNEE.name()
                    : NotificationStatus.ECHOUEE.name());
            notification.setLastError(truncate(e.getMessage()));
            outboxRepository.save(notification);

            log.warn("Notification {} en échec (tentative {}/{}) : {}",
                    notification.getId(), notification.getAttempts(), notificationConfig.getMaxAttempts(), e.getMessage());
            return false;
        }
    }

    /**
     * Langue du destinataire, ou le français à défaut de préférence enregistrée.
     *
     * <p>La colonne {@code notification_preference.language} existait depuis la V18
     * et n'était <strong>lue par personne</strong> : le sujet et le corps étaient
     * composés en français quelle qu'en soit la valeur. L'utilisateur pouvait la
     * régler et constater que rien ne changeait — le pire des cas, puisqu'il en
     * concluait que le réglage ne servait à rien plutôt qu'à un défaut.
     */
    private NotificationLanguage languageOf(RecipientResolver.Target target) {
        if (target == null || target.preference() == null) {
            return NotificationLanguage.DEFAULT;
        }
        return NotificationLanguage.from(target.preference().getLanguage());
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }
}
