package com.sni.bilanga.notification.dto.request;

import com.sni.bilanga.enums.AlertLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Réglages de notification d'un utilisateur.
 *
 * <p>Tous les champs sont facultatifs, et un champ absent signifie « appliquer
 * le réglage global », jamais « ne rien envoyer » : un formulaire incomplet ne
 * doit pas rendre le système muet pour celui qui l'a rempli à moitié.
 */
@Data
public class NotificationPreferenceRequest {

    /**
     * Seuil personnel, qui prime sur le seuil global.
     * Un agronome veut tout voir ; un exploitant ne veut être dérangé que pour
     * ce qui appelle une action immédiate.
     */
    private AlertLevel minLevel;

    /** Canaux retenus ({@code SMS}, {@code LOG}) ; vide signifie « tous ». */
    private List<String> channels;

    /**
     * Langue des notifications : {@code fr}, {@code ln} (lingala) ou {@code kg}
     * (kituba).
     *
     * <p><strong>Ce champ existait et n'était lu par personne.</strong> La colonne
     * est là depuis la V18 ; le message était composé en français quelle qu'en soit
     * la valeur. L'utilisateur pouvait la régler et constater que rien ne changeait —
     * le pire des cas, puisqu'il en concluait que le réglage était décoratif plutôt
     * qu'à un défaut.
     *
     * <p><strong>Ce qui est traduit</strong> : l'urgence, la parcelle, l'appel à
     * agir — ce qui décide de l'action et ce qu'on lit en premier sur un téléphone
     * simple. <strong>Ce qui ne l'est pas</strong> : le constat agronomique produit
     * par les moteurs, qui reste en français, et le message le dit explicitement.
     * Traduire une prose composée à la volée exigerait de traduire chaque règle de
     * la base de connaissance à trois exemplaires, et une traduction qui dérive
     * donnerait un conseil <em>faux</em> dans la langue que la personne comprend le
     * mieux — donc celui qu'elle suivrait.
     *
     * <p>Insensible à la casse et aux formes régionales ({@code fr-CG} vaut
     * {@code fr}). Une valeur inconnue retombe sur le français sans échouer :
     * mieux vaut un message en français qu'aucun message.
     */
    @Size(max = 10, message = "Le code de langue ne peut dépasser 10 caractères")
    private String language;

    /**
     * Heures de silence, en heure locale. La plage peut enjamber minuit
     * ({@code 22 → 6}). Une alerte critique passe outre.
     */
    @Min(value = 0, message = "L'heure de début doit être comprise entre 0 et 23")
    @Max(value = 23, message = "L'heure de début doit être comprise entre 0 et 23")
    private Integer quietFromHour;

    @Min(value = 0, message = "L'heure de fin doit être comprise entre 0 et 23")
    @Max(value = 23, message = "L'heure de fin doit être comprise entre 0 et 23")
    private Integer quietToHour;
}
