package com.sni.bilanga.notification.service;

import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.model.Alert;
import com.sni.bilanga.enums.AlertLevel;
import com.sni.bilanga.notification.channel.HttpSmsChannel;
import com.sni.bilanga.notification.model.NotificationPreference;
import com.sni.bilanga.notification.repository.NotificationPreferenceRepository;
import com.sni.bilanga.security.admin.user.model.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Décide à qui, par quel canal et à quel moment une alerte doit être adressée.
 *
 * <p><strong>Le manque que cela comble.</strong> {@code notification_outbox}
 * porte une colonne {@code recipient} depuis la migration V15 ; elle n'a jamais
 * été renseignée. Le seul canal implémenté écrivait dans les journaux et n'avait
 * donc besoin de personne — ce qui a masqué le fait que le système ne savait pas
 * à qui il s'adressait. Dès qu'un canal réel apparaît, la question devient
 * bloquante.
 *
 * <p><strong>Et pourquoi les préférences ne sont pas un confort.</strong>
 * Notifier tout le monde de la même façon revient à ne notifier personne :
 * celui qu'on réveille à trois heures du matin pour une situation qui pouvait
 * attendre coupe ses notifications, et n'apprendra pas non plus la critique du
 * lendemain. Le seuil personnel et les heures de silence protègent la capacité
 * du système à être entendu quand il le faut.
 */
@Component
@RequiredArgsConstructor
public class RecipientResolver {

    private final NotificationPreferenceRepository preferenceRepository;
    private final BilangaProperties.Notification notificationConfig;
    private final AppProperties appProperties;

    /**
     * Ce qu'il faut savoir pour enfiler — ou non — une notification.
     *
     * @param user            propriétaire de la parcelle, ou {@code null}
     * @param preference      ses réglages, ou {@code null} s'il n'en a pas défini
     * @param acceptedChannels canaux retenus ; vide signifie « tous »
     */
    public record Target(Users user, NotificationPreference preference, Set<String> acceptedChannels) {

        public static final Target NONE = new Target(null, null, Set.of());
    }

    public Target resolve(Alert alert) {
        Users owner = alert.getPlot() == null ? null : alert.getPlot().getUser();

        // Une alerte affectée prime sur le propriétaire : si quelqu'un s'est vu
        // confier le traitement, c'est lui qu'il faut prévenir, pas le titulaire
        // du titre foncier.
        Users addressee = alert.getAssignedTo() != null ? alert.getAssignedTo() : owner;
        if (addressee == null) {
            return Target.NONE;
        }

        NotificationPreference preference =
                preferenceRepository.findByUser_Id(addressee.getId()).orElse(null);

        return new Target(addressee, preference, channelsOf(preference));
    }

    // ============================================================
    // Filtres
    // ============================================================

    /**
     * Le seuil personnel prime sur le seuil global.
     *
     * <p>Un agronome-conseil veut tout voir ; un exploitant ne veut être dérangé
     * que pour ce qui appelle une action immédiate. Sans réglage personnel, le
     * seuil global s'applique — l'absence de préférence ne rend jamais le
     * système muet.
     */
    public boolean warrants(Target target, Alert alert) {
        AlertLevel level = AlertLevel.from(alert.getLevel());
        if (level == null) {
            return false;
        }

        String threshold = Optional.ofNullable(target.preference())
                .map(NotificationPreference::getMinLevel)
                .filter(value -> !value.isBlank())
                .orElse(notificationConfig.getMinLevel());

        AlertLevel minimum = AlertLevel.from(threshold);
        return minimum == null || level.isAtLeast(minimum);
    }

    /** Vide signifie « tous les canaux disponibles », jamais « aucun ». */
    public boolean accepts(Target target, String channelName) {
        return target.acceptedChannels().isEmpty()
                || target.acceptedChannels().contains(channelName.toUpperCase(Locale.ROOT));
    }

    /**
     * Adresse à employer sur ce canal.
     *
     * <p>Renvoie {@code null} lorsqu'on ne sait pas joindre la personne par ce
     * moyen — un exploitant sans numéro de téléphone, par exemple. L'appelant
     * n'enfile alors rien : une ligne en attente sans destinataire n'échouerait
     * que pour être reprise, indéfiniment, sans jamais aboutir.
     */
    public String addressFor(Target target, String channelName) {
        if (target.user() == null) {
            return null;
        }
        if (HttpSmsChannel.NAME.equalsIgnoreCase(channelName)) {
            return blankToNull(target.user().getPhone());
        }
        return blankToNull(target.user().getEmail());
    }

    // ============================================================
    // Heures de silence
    // ============================================================

    /**
     * Instant avant lequel la notification ne doit pas partir, ou {@code null}
     * pour un envoi immédiat.
     *
     * <p><strong>Le critique passe outre.</strong> Reporter une alerte critique
     * la viderait de son sens : le niveau critique ne signifie rien s'il attend
     * six heures comme les autres.
     *
     * <p>La plage peut enjamber minuit ({@code 22 → 6}), cas le plus courant ;
     * les deux configurations sont traitées explicitement plutôt que par une
     * comparaison unique qui n'en couvrirait qu'une.
     */
    public Instant deferralFor(Target target, Alert alert) {
        NotificationPreference preference = target.preference();
        if (preference == null
                || preference.getQuietFromHour() == null
                || preference.getQuietToHour() == null) {
            return null;
        }

        AlertLevel level = AlertLevel.from(alert.getLevel());
        if (level == AlertLevel.CRITIQUE) {
            return null;
        }

        ZoneId zone = ZoneId.of(appProperties.getTimeZone());
        ZonedDateTime now = ZonedDateTime.now(zone);

        int from = preference.getQuietFromHour();
        int to = preference.getQuietToHour();
        int hour = now.getHour();

        boolean silent = from <= to
                ? hour >= from && hour < to             // 1 → 6
                : hour >= from || hour < to;            // 22 → 6, enjambe minuit

        if (!silent) {
            return null;
        }

        ZonedDateTime resume = now.with(LocalTime.of(to, 0));
        if (!resume.isAfter(now)) {
            resume = resume.plusDays(1);
        }
        return resume.toInstant();
    }

    /** Fenêtre de regroupement en cours, employée pour composer l'empreinte. */
    public long groupingSlot(Instant moment) {
        long windowMillis = Duration.ofMinutes(notificationConfig.getGroupingWindowMinutes()).toMillis();
        return moment.toEpochMilli() / windowMillis;
    }

    // ============================================================
    // Interne
    // ============================================================
    private Set<String> channelsOf(NotificationPreference preference) {
        if (preference == null || preference.getChannels() == null || preference.getChannels().isBlank()) {
            return Set.of();
        }
        return Arrays.stream(preference.getChannels().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
