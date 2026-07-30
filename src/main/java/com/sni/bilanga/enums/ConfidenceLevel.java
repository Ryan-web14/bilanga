package com.sni.bilanga.enums;

/**
 * Confiance accordée à une prédiction du modèle.
 *
 * En deçà de {@code MOYENNE}, le diagnostic n'est pas jugé fiable et ne peut pas
 * lever d'alerte : l'exploitant se déplacerait sur la foi d'une conclusion que le
 * système lui-même ne soutient pas.
 */
public enum ConfidenceLevel {

    FAIBLE("Faible", 0),
    MOYENNE("Moyenne", 1),
    ELEVEE("Élevée", 2);

    private final String label;
    private final int rank;

    ConfidenceLevel(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    public int getRank() {
        return rank;
    }

    public static ConfidenceLevel from(String value) {
        return DomainEnums.parse(ConfidenceLevel.class, value);
    }
}
