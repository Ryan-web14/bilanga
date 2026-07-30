package com.sni.bilanga.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Conversion entre les valeurs textuelles portées par la base et les
 * énumérations du domaine.
 *
 * Les colonnes restent en {@code VARCHAR} — le vocabulaire est verrouillé par des
 * contraintes {@code CHECK}. Ce point de passage unique évite que chaque appelant
 * réinvente sa propre comparaison de chaînes, source des divergences de casse
 * qu'on trouvait jusqu'ici (« HAUTE », « haute », « Haute »).
 */
public final class DomainEnums {

    private DomainEnums() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Tolérante à la casse et aux espaces. Renvoie {@code null} sur une valeur
     * absente ou inconnue plutôt que de lever : les valeurs viennent de données
     * historiques qu'on ne veut pas voir faire échouer une lecture.
     */
    public static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalized)) {
                return constant;
            }
        }
        return null;
    }

    /** Vrai si la valeur brute correspond à la constante attendue. */
    public static <E extends Enum<E>> boolean matches(E expected, String value) {
        return expected != null && expected == parse(expected.getDeclaringClass(), value);
    }

    /** Nom de la constante, ou {@code null} — pour écrire une entité sans test préalable. */
    public static String nameOf(Enum<?> constant) {
        return constant == null ? null : constant.name();
    }

    /** Vocabulaire accepté, destiné aux messages d'erreur adressés à l'appelant. */
    public static String accepted(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
