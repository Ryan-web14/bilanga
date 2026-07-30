package com.sni.bilanga.farm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Une entrée du journal d'un cycle, prête à afficher.
 *
 * <p>Les entrées {@code before} renseigné vers {@code after: null} décrivent un
 * effacement <strong>explicite</strong>, demandé par {@code clearFields}. La mise à
 * jour est partielle : un champ absent d'une requête n'est pas touché et ne produit
 * aucune entrée.
 *
 * <p>Ce n'était pas le cas avant : le service écrasait inconditionnellement les champs
 * omis, si bien qu'un {@code PUT} partiel effaçait la surface plantée sans le dire — et
 * rendait le bilan économique incomparable des semaines plus tard. Corrigé.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CropJournalEntry {

    private Long id;
    private Long cropId;
    private Long plotId;

    /**
     * Vocabulaire : {@code CREATION}, {@code MODIFICATION}, {@code STADE_RECALCULE},
     * {@code CLOTURE}, {@code CLONAGE}.
     */
    private String eventType;
    private String eventLabel;

    /**
     * Vrai si l'entrée résulte d'une décision humaine.
     *
     * <p>Faux sur {@code STADE_RECALCULE} : c'est le temps qui passe, pas quelqu'un qui
     * agit. Les afficher au même rang ferait porter à un utilisateur des changements qui
     * ne sont pas les siens — et noierait les vraies modifications sous les recalculs.
     */
    private Boolean humanAction;

    /**
     * Ce qui a changé : {@code { champ: { before, after } }}.
     *
     * <p>Sur une {@code CREATION} et un {@code CLONAGE}, {@code before} vaut
     * systématiquement {@code null} — c'est l'état initial, exprimé sous la même forme
     * qu'un diff pour qu'un seul gabarit d'affichage suffise.
     *
     * <p>⚠️ Les identifiants y sont des <strong>chaînes</strong>, comme partout dans
     * l'API.
     */
    private Map<String, Object> changes;

    /** Nombre de champs touchés — pour replier une entrée volumineuse. */
    private Integer changeCount;

    private String reason;

    /**
     * Verrou optimiste du cycle <strong>avant</strong> cette écriture.
     *
     * <p>Permet d'ordonner deux entrées portant le même horodatage, et de repérer une
     * modification concurrente.
     */
    private Long cropVersion;

    private Long changedBy;

    /**
     * Adresse de l'auteur, conservée en clair.
     *
     * <p>Reste renseignée même si le compte a été supprimé depuis : la FK est
     * {@code ON DELETE SET NULL}, et un journal qui perd le nom de son auteur perd sa
     * raison d'être.
     */
    private String changedByEmail;

    private Instant changedAt;
}
