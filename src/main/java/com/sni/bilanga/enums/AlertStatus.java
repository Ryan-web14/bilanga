package com.sni.bilanga.enums;

import java.util.Set;

/**
 * Cycle de vie d'une alerte : NOUVELLE → ACQUITTEE → RESOLUE.
 *
 * Une alerte peut être résolue sans avoir été acquittée — c'est le cas quand la
 * situation se normalise d'elle-même avant que quiconque l'ait vue.
 */
public enum AlertStatus {

    NOUVELLE("Nouvelle"),
    ACQUITTEE("Acquittée"),
    RESOLUE("Résolue");

    /** Statuts d'une alerte encore à traiter. */
    public static final Set<String> OPEN_NAMES = Set.of(NOUVELLE.name(), ACQUITTEE.name());

    private final String label;

    AlertStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isOpen() {
        return this != RESOLUE;
    }

    /** Transitions autorisées. Une alerte résolue est définitive. */
    public boolean canTransitionTo(AlertStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case NOUVELLE -> true;
            case ACQUITTEE -> target == RESOLUE;
            case RESOLUE -> false;
        };
    }

    public static AlertStatus from(String value) {
        return DomainEnums.parse(AlertStatus.class, value);
    }
}
