package com.sni.bilanga.utils.format;

import java.time.Instant;

/**
 * Bornes par défaut d'un intervalle de recherche facultatif.
 *
 * <p>Écrire {@code (:from is null or col >= :from)} paraissait naturel mais
 * posait deux problèmes. PostgreSQL ne peut pas inférer le type d'un paramètre
 * qui n'apparaît que dans une comparaison à {@code NULL} — la requête échouait
 * avec {@code could not determine data type of parameter}. Et même typé, ce
 * prédicat interdit au planificateur d'utiliser l'index sur la colonne de date,
 * qui est précisément celui qui compte sur une série temporelle.
 *
 * <p>Substituer une borne ouverte ramène la clause à un simple encadrement,
 * typé et indexable. Les bornes sont volontairement hors de toute donnée
 * plausible tout en restant représentables par un {@code timestamp} PostgreSQL.
 */
public final class TimeRange {

    /** Borne basse ouverte : antérieure à toute donnée du système. */
    public static final Instant MIN = Instant.EPOCH;

    /** Borne haute ouverte, bien en deçà de la limite de PostgreSQL. */
    public static final Instant MAX = Instant.parse("9999-12-31T23:59:59Z");

    private TimeRange() {
        throw new IllegalStateException("Utility class");
    }

    public static Instant from(Instant value) {
        return value == null ? MIN : value;
    }

    public static Instant to(Instant value) {
        return value == null ? MAX : value;
    }
}
