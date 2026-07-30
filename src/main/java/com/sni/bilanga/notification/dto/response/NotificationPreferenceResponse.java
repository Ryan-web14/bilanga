package com.sni.bilanga.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class NotificationPreferenceResponse {

    private Long userId;

    private String minLevel;
    private String minLevelLabel;

    /** Vrai lorsque le seuil affiché vient du réglage global, faute de réglage personnel. */
    private Boolean minLevelInherited;

    private List<String> channels;

    /** Canaux réellement disponibles côté serveur, pour que le client sache quoi proposer. */
    private List<String> availableChannels;

    private String language;

    /** Libellé de la langue retenue : « Français », « Lingala », « Kituba ». */
    private String languageLabel;

    /**
     * Langues proposables, avec leur code et leur libellé.
     *
     * <p>Servi par le serveur plutôt que codé en dur côté client : ajouter une
     * langue doit être une modification de {@code NotificationLanguage}, pas une
     * livraison frontend. Un menu déroulant construit à la main dériverait de ce
     * que le serveur sait réellement composer.
     */
    private java.util.Map<String, String> availableLanguages;

    /**
     * Ce que la traduction couvre, en une phrase à afficher sous le sélecteur.
     *
     * <p><strong>À afficher</strong> : sans elle, un exploitant qui choisit le
     * lingala et reçoit un détail technique en français conclut à un bogue. Le dire
     * transforme une limite en choix assumé — et c'est un choix, non une paresse :
     * traduire la prose des moteurs à trois exemplaires reviendrait à risquer un
     * conseil faux dans la langue la mieux comprise.
     */
    private String languageScopeNote;

    private Integer quietFromHour;
    private Integer quietToHour;

    /**
     * Formulation en clair des heures de silence, ou {@code null} si aucune
     * n'est définie — pour éviter que chaque client reconstruise la phrase, et
     * se trompe sur le cas qui enjambe minuit.
     */
    private String quietHoursLabel;

    /**
     * Faux si l'utilisateur n'a pas de numéro : le canal SMS a beau être retenu,
     * rien ne pourra lui être envoyé. Le dire ici évite de laisser croire à une
     * couverture qui n'existe pas.
     */
    private Boolean smsReachable;

    private Instant updatedAt;
}
