package com.sni.bilanga.enums;

/**
 * Urgence d'un conseil. Le rang commande l'ordre de restitution : un conseil
 * urgent doit apparaître en tête de réponse, avant ceux qui peuvent attendre.
 */
public enum RecommendationPriority {

    HAUTE("Haute", 0),
    MOYENNE("Moyenne", 1),
    BASSE("Basse", 2);

    /** Rang attribué à une priorité absente ou non reconnue : elle passe en dernier. */
    public static final int UNKNOWN_RANK = 3;

    private final String label;
    private final int rank;

    RecommendationPriority(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    /** Croissant : 0 est le plus urgent. */
    public int getRank() {
        return rank;
    }

    public boolean isUrgent() {
        return this == HAUTE;
    }

    public static RecommendationPriority from(String value) {
        return DomainEnums.parse(RecommendationPriority.class, value);
    }

    /** Rang d'une priorité brute, sans lever d'exception sur une valeur inconnue. */
    public static int rankOf(String value) {
        RecommendationPriority priority = from(value);
        return priority == null ? UNKNOWN_RANK : priority.rank;
    }

    public static boolean isUrgent(String value) {
        return HAUTE == from(value);
    }
}