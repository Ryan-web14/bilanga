package com.sni.bilanga.notification.service;

import com.sni.bilanga.enums.NotificationLanguage;
import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.enums.AlertLevel;
import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.notification.channel.HttpSmsChannel;
import com.sni.bilanga.notification.channel.NotificationChannel;
import com.sni.bilanga.notification.dto.request.NotificationPreferenceRequest;
import com.sni.bilanga.notification.dto.response.NotificationPreferenceResponse;
import com.sni.bilanga.notification.model.NotificationPreference;
import com.sni.bilanga.notification.repository.NotificationPreferenceRepository;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Consultation et mise à jour des réglages de notification d'un utilisateur.
 *
 * <p>Un utilisateur sans réglage n'est pas un utilisateur muet : la lecture
 * renvoie alors les valeurs globales, en le signalant
 * ({@code minLevelInherited}). C'est ce qui permet d'afficher un formulaire
 * pré-rempli et cohérent avant toute saisie.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final List<NotificationChannel> channels;
    private final BilangaProperties.Notification notificationConfig;

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse find(Long userId) {
        Users user = requireUser(userId);
        return toResponse(user, preferenceRepository.findByUser_Id(userId).orElse(null));
    }

    /**
     * Crée ou remplace les réglages.
     *
     * <p>Remplacement complet, et non fusion : une préférence à moitié modifiée
     * laisserait l'utilisateur incapable de savoir ce qui s'applique
     * réellement — ce qui est exactement le défaut qu'on cherche à corriger.
     */
    @Transactional
    public NotificationPreferenceResponse save(Long userId, NotificationPreferenceRequest request) {
        Users user = requireUser(userId);
        requireCompleteQuietRange(request);

        NotificationPreference preference = preferenceRepository.findByUser_Id(userId)
                .orElseGet(() -> NotificationPreference.builder().user(user).build());

        preference.setMinLevel(DomainEnums.nameOf(request.getMinLevel()));
        preference.setChannels(joinChannels(request.getChannels()));
        // Normalisée à l'écriture, et non seulement à la lecture : stocker « FR-CG »
        // tel quel obligerait chaque lecteur futur à refaire la normalisation, et
        // la première à l'oublier composerait le message en français par défaut sans
        // qu'on sache pourquoi. La base porte donc toujours un code du vocabulaire.
        preference.setLanguage(NotificationLanguage.from(request.getLanguage()).getCode());
        preference.setQuietFromHour(request.getQuietFromHour());
        preference.setQuietToHour(request.getQuietToHour());
        preference.setUpdatedAt(Instant.now());

        return toResponse(user, preferenceRepository.save(preference));
    }

    /**
     * Une borne sans l'autre ne décrit aucune plage.
     *
     * La base porte la même contrainte ; la refuser ici permet d'expliquer
     * pourquoi, là où l'erreur de contrainte ne dirait rien d'utilisable.
     */
    private void requireCompleteQuietRange(NotificationPreferenceRequest request) {
        boolean fromSet = request.getQuietFromHour() != null;
        boolean toSet = request.getQuietToHour() != null;

        if (fromSet != toSet) {
            throw new BusinessRuleException(
                    "Les heures de silence demandent une heure de début ET une heure de fin. "
                    + "Laissez les deux vides pour ne pas en définir.");
        }
    }

    private String joinChannels(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return null;   // nul = tous les canaux, jamais aucun
        }
        return requested.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
    }

    private NotificationPreferenceResponse toResponse(Users user, NotificationPreference preference) {
        boolean inherited = preference == null
                || preference.getMinLevel() == null
                || preference.getMinLevel().isBlank();

        String effectiveLevel = inherited
                ? notificationConfig.getMinLevel()
                : preference.getMinLevel();

        AlertLevel level = AlertLevel.from(effectiveLevel);
        NotificationLanguage language =
                NotificationLanguage.from(preference == null ? null : preference.getLanguage());

        return NotificationPreferenceResponse.builder()
                .userId(user.getId())
                .minLevel(effectiveLevel)
                .minLevelLabel(level == null ? null : level.getLabel())
                .minLevelInherited(inherited)
                .channels(splitChannels(preference))
                .availableChannels(channels.stream()
                        .filter(NotificationChannel::isAvailable)
                        .map(NotificationChannel::name)
                        .toList())
                .language(language.getCode())
                .languageLabel(language.getLabel())
                // Servi par le serveur plutôt que codé en dur côté client :
                // ajouter une langue doit être une modification de
                // NotificationLanguage, pas une livraison frontend.
                .availableLanguages(java.util.Arrays.stream(NotificationLanguage.values())
                        .collect(java.util.stream.Collectors.toMap(
                                NotificationLanguage::getCode,
                                NotificationLanguage::getLabel,
                                (a, b) -> a,
                                java.util.LinkedHashMap::new)))
                .languageScopeNote(LANGUAGE_SCOPE_NOTE)
                .quietFromHour(preference == null ? null : preference.getQuietFromHour())
                .quietToHour(preference == null ? null : preference.getQuietToHour())
                .quietHoursLabel(quietHoursLabel(preference))
                .smsReachable(user.getPhone() != null && !user.getPhone().isBlank())
                .updatedAt(preference == null ? null : preference.getUpdatedAt())
                .build();
    }

    private List<String> splitChannels(NotificationPreference preference) {
        if (preference == null || preference.getChannels() == null || preference.getChannels().isBlank()) {
            return List.of();
        }
        return Arrays.stream(preference.getChannels().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /**
     * Formule la plage en clair, y compris quand elle enjambe minuit — le cas
     * courant, et celui que les clients formulent de travers.
     */
    private String quietHoursLabel(NotificationPreference preference) {
        if (preference == null
                || preference.getQuietFromHour() == null
                || preference.getQuietToHour() == null) {
            return null;
        }

        int from = preference.getQuietFromHour();
        int to = preference.getQuietToHour();
        String span = from <= to
                ? String.format("de %02dh à %02dh", from, to)
                : String.format("de %02dh à %02dh le lendemain", from, to);

        return "Aucune notification " + span
               + ", sauf alerte critique — qui passe outre, sans quoi ce niveau ne voudrait rien dire.";
    }

    private Users requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessRuleException(
                    "Aucun utilisateur authentifié : les préférences de notification sont personnelles.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));
    }

    /** Le canal SMS n'a de sens que si l'utilisateur a un numéro. */
    public boolean smsConfigured() {
        return channels.stream()
                .anyMatch(c -> HttpSmsChannel.NAME.equals(c.name()) && c.isAvailable());
    }
    /**
     * Ce que la traduction couvre, dit une fois et servi au client.
     *
     * <p>Sans cette phrase, un exploitant qui choisit le lingala et reçoit un
     * détail technique en français conclut à un bogue. Le dire transforme une
     * limite en choix assumé — et c'est un choix : traduire la prose composée à
     * la volée par les moteurs, à trois exemplaires, ferait risquer un conseil
     * FAUX dans la langue que la personne comprend le mieux.
     */
    private static final String LANGUAGE_SCOPE_NOTE =
            "L'urgence, la parcelle et l'action à mener sont traduites. Le détail " +
            "technique du diagnostic reste en français : il est composé automatiquement " +
            "à partir des mesures, et une traduction approximative y donnerait un " +
            "conseil faux. Votre conseiller peut vous l'expliquer.";

}
