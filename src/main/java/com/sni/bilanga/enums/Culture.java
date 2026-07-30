package com.sni.bilanga.enums;

import java.util.Locale;

/**
 * Cultures couvertes par la base de connaissance.
 *
 * <p><strong>Deux représentations coexistent, et c'est assumé :</strong> la base
 * stocke les cultures en minuscules — la base de connaissance, ses règles et le
 * microservice d'inférence les attendent ainsi — tandis que l'API expose la
 * constante en majuscules.
 *
 * <p>Le contrat est donc : <em>ce que le client envoie est ce qu'il reçoit</em>,
 * quelle que soit la casse qu'il emploie. La conversion est faite ici, à un
 * seul endroit, au lieu d'être laissée à la charge de chaque appelant — c'est
 * ce qui faisait qu'un client envoyant {@code TOMATE} recevait {@code tomate}
 * et voyait ses comparaisons échouer en silence.
 */
public enum Culture {

    TOMATE("Tomate"),
    MANIOC("Manioc");

    /** Joker des règles valables quelle que soit la culture. */
    public static final String ANY = "*";

    private final String label;

    Culture(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Forme attendue par la base et par les moteurs de connaissance. */
    public String storageName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Tolérante à la casse et aux espaces ; {@code null} si la valeur est inconnue. */
    public static Culture from(String value) {
        return DomainEnums.parse(Culture.class, value);
    }

    /** Forme de stockage, ou {@code null}. */
    public static String toStorage(Culture culture) {
        return culture == null ? null : culture.storageName();
    }

    /**
     * Forme canonique exposée par l'API.
     *
     * Le joker {@code *} et toute valeur non reconnue sont renvoyés inchangés :
     * la base de connaissance porte des règles crop-agnostiques, et des données
     * historiques ne doivent pas disparaître d'une réponse parce qu'elles ne
     * correspondent à aucune constante.
     */
    public static String canonical(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        Culture culture = from(stored);
        return culture == null ? stored.trim() : culture.name();
    }

    /** Libellé lisible, ou {@code null} si la valeur n'est pas une culture connue. */
    public static String labelOf(String stored) {
        Culture culture = from(stored);
        return culture == null ? null : culture.getLabel();
    }
}
