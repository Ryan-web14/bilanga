package com.sni.bilanga.notification.channel;

import com.sni.bilanga.notification.model.NotificationOutbox;

/**
 * Voie d'acheminement d'une notification.
 *
 * Le point d'extension attendu : ajouter le courriel, le SMS ou WhatsApp
 * revient à fournir une implémentation de plus, sans toucher au moteur d'alerte.
 * Une implémentation qui échoue lève simplement une exception — la reprise est
 * la charge de l'expéditeur, pas du canal.
 */
public interface NotificationChannel {

    /** Identifiant stocké dans {@code notification_outbox.channel}. */
    String name();

    /** Faux si le canal n'est pas configuré : inutile de compter ses échecs. */
    boolean isAvailable();

    void send(NotificationOutbox notification);
}
