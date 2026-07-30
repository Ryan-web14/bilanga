package com.sni.bilanga.enums;

/**
 * Suite donnée à un conseil par l'exploitant.
 *
 * Sans ce retour, rien ne permet de savoir si le moteur conseille juste :
 * un conseil systématiquement ignoré est le signe d'une règle à revoir.
 */
public enum RecommendationStatus {

    ACTIVE("À traiter"),
    APPLIQUEE("Appliquée"),
    IGNOREE("Ignorée");

    private final String label;

    RecommendationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Un conseil clos ne change plus d'état : la trace resterait ambiguë. */
    public boolean isFinal() {
        return this != ACTIVE;
    }

    public static RecommendationStatus from(String value) {
        return DomainEnums.parse(RecommendationStatus.class, value);
    }
}
