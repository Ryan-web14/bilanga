package com.sni.bilanga.audit.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Compare deux états d'un même objet, champ par champ, par réflexion.
 *
 * <p><strong>À quoi cela sert.</strong> {@code audit_log} disait <em>qui</em> a
 * fait <em>quoi</em>, jamais <em>quel changement</em>. Savoir qu'Aline a modifié
 * le rôle {@code ADMIN} à 14 h 02 ne dit pas ce qu'elle y a changé — or c'est
 * exactement la question qu'on se pose en relisant un journal d'audit après un
 * incident.
 *
 * <h2>Deux défauts corrigés avant de brancher cette classe</h2>
 *
 * <p><strong>1. {@code Map.of} refuse les valeurs nulles.</strong> L'ancienne
 * version écrivait {@code Map.of("before", b, "after", a)}. Or les cartes
 * immuables du JDK lèvent une {@code NullPointerException} sur une valeur nulle —
 * et le cas le plus fréquent d'un diff est précisément un champ qui passe de
 * {@code null} à une valeur, ou l'inverse. Brancher la classe telle quelle aurait
 * fait échouer l'aspect d'audit dès le premier renseignement d'un champ
 * optionnel, c'est-à-dire tout de suite. Un {@code LinkedHashMap} accepte les
 * nulls, et son ordre est stable — ce qui rend le JSON comparable d'une entrée à
 * l'autre.
 *
 * <p><strong>2. Aucune protection sur les secrets.</strong> Un diff par réflexion
 * sur un objet utilisateur recopierait le mot de passe haché, le jeton, le
 * secret — dans une table faite pour être lue, et conservée longtemps.
 * {@link #REDACTED_HINTS} les remplace par un marqueur : le journal dit
 * <em>que</em> le champ a changé, sans dire par quoi. C'est l'information utile à
 * l'audit, et la seule qu'il ait à porter.
 *
 * <p>Les champs statiques et synthétiques sont ignorés : les premiers ne décrivent
 * pas l'instance, les seconds sont ajoutés par le compilateur et n'ont aucun sens
 * métier.
 */
public final class AuditDiffUtil {

    /**
     * Fragments de nom de champ dont la valeur ne doit jamais être journalisée.
     * Comparés en minuscules, par inclusion : {@code passwordHash},
     * {@code refreshTokenHash} et {@code jwtSecret} sont tous couverts.
     */
    private static final List<String> REDACTED_HINTS = List.of(
            "password", "secret", "token", "credential", "apikey", "api_key", "salt");

    private static final String REDACTED = "«valeur masquée»";

    private AuditDiffUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Champs dont la valeur a changé, sous la forme
     * {@code {champ: {before: …, after: …}}}.
     *
     * <p>Rend une carte <strong>vide</strong> — jamais {@code null} — quand rien
     * n'a changé ou qu'un des deux états manque : l'appelant écrit le résultat tel
     * quel dans une colonne {@code jsonb}, et devoir y tester la nullité
     * dupliquerait la précaution partout.
     */
    public static Map<String, Object> diff(Object before, Object after) {
        Map<String, Object> changes = new LinkedHashMap<>();

        if (before == null || after == null || !before.getClass().equals(after.getClass())) {
            return changes;
        }

        for (Field field : before.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object oldValue = field.get(before);
                Object newValue = field.get(after);

                if (Objects.equals(oldValue, newValue)) {
                    continue;
                }

                Map<String, Object> change = new LinkedHashMap<>();
                boolean sensitive = isSensitive(field.getName());
                change.put("before", sensitive ? REDACTED : display(oldValue));
                change.put("after", sensitive ? REDACTED : display(newValue));

                changes.put(field.getName(), change);
            } catch (IllegalAccessException | RuntimeException ignored) {
                // Un champ inaccessible — module fermé, sécurité de la JVM — ne
                // doit pas faire échouer l'audit de l'opération : celle-ci a
                // abouti, et c'est d'abord cela qu'il faut consigner.
            }
        }
        return changes;
    }

    private static boolean isSensitive(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return REDACTED_HINTS.stream().anyMatch(normalized::contains);
    }

    /**
     * Représentation destinée au {@code jsonb}.
     *
     * <p>Les types simples passent tels quels ; le reste est converti en texte.
     * Confier une entité JPA entière au sérialiseur entraînerait ses associations
     * paresseuses — et, hors transaction, lèverait. Le nom du champ et sa valeur
     * lisible suffisent à l'audit.
     */
    private static Object display(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        return String.valueOf(value);
    }
}
