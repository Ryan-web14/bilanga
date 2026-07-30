package com.sni.bilanga.enums;

/**
 * Échelle de gravité partagée par le moteur de risque et le moteur agronomique.
 *
 * Distincte de {@link AlertLevel} : une gravité qualifie une <em>observation</em>
 * (ce risque est élevé), un niveau d'alerte qualifie une <em>situation à traiter</em>.
 */
public enum Severity {

    FAIBLE("Faible", 0),
    MODERE("Modéré", 1),
    ELEVE("Élevé", 2);

    private final String label;
    private final int rank;

    Severity(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    /** Croissant : plus le rang est haut, plus la situation est grave. */
    public int getRank() {
        return rank;
    }

    public boolean isAtLeast(Severity other) {
        return other != null && this.rank >= other.rank;
    }

    public static Severity from(String value) {
        return DomainEnums.parse(Severity.class, value);
    }
}
