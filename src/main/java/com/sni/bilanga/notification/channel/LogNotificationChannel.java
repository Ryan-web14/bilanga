package com.sni.bilanga.notification.channel;

import com.sni.bilanga.notification.model.NotificationOutbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Canal de repli : la notification est journalisée.
 *
 * Toujours disponible, il garantit qu'aucune alerte ne reste totalement muette
 * tant qu'aucun canal réel n'est configuré. C'est aussi ce qui rend le
 * mécanisme observable en développement sans dépendre d'un serveur de courriel.
 */
@Slf4j
@Component
public class LogNotificationChannel implements NotificationChannel {

    public static final String NAME = "LOG";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void send(NotificationOutbox notification) {
        log.warn("[NOTIFICATION {}] parcelle={} alerte={} · {} — {}",
                notification.getLevel(),
                notification.getPlotId(),
                notification.getAlertId(),
                notification.getSubject(),
                notification.getBody());
    }
}
