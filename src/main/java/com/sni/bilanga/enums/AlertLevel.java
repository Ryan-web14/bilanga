package com.sni.bilanga.enums;

/**
 * Gravité d'une alerte ouverte.
 *
 * Les libellés sont en français parce qu'ils sont stockés tels quels depuis la
 * migration V2 et qu'ils remontent jusqu'à l'exploitant.
 */
public enum AlertLevel {

    MOYENNE("Moyenne", 0),
    ELEVEE("Élevée", 1),
    CRITIQUE("Critique", 2);

    private final String label;
    private final int rank;

    AlertLevel(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    public int getRank() {
        return rank;
    }

    public boolean isAtLeast(AlertLevel other) {
        return other != null && this.rank >= other.rank;
    }

    /** Niveau immédiatement supérieur, ou le niveau courant s'il est déjà au sommet. */
    public AlertLevel escalated() {
        return switch (this) {
            case MOYENNE -> ELEVEE;
            case ELEVEE, CRITIQUE -> CRITIQUE;
        };
    }

    public static AlertLevel from(String value) {
        return DomainEnums.parse(AlertLevel.class, value);
    }
}
