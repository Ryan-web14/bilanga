package com.sni.bilanga.enums;

/**
 * Synthèse de l'état d'une parcelle pour les tableaux de bord.
 *
 * L'ordre de précédence est intentionnel et ne doit pas être réarrangé : une
 * alerte ouverte prime sur un risque élevé, qui prime sur un diagnostic anormal.
 * C'est la seule information qui appelle une action immédiate.
 */
public enum OverallStatus {

    SANS_DONNEES("Sans données", 0),
    NORMAL("Normal", 1),
    VIGILANCE("Vigilance", 2),
    ALERTE("Alerte", 3),
    CRITIQUE("Critique", 4);

    private final String label;
    private final int rank;

    OverallStatus(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    /** Croissant selon l'urgence, pour trier ou agréger plusieurs parcelles. */
    public int getRank() {
        return rank;
    }

    public boolean needsAttention() {
        return this == VIGILANCE || this == ALERTE || this == CRITIQUE;
    }

    public static OverallStatus from(String value) {
        return DomainEnums.parse(OverallStatus.class, value);
    }
}
