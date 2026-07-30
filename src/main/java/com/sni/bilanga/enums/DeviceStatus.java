package com.sni.bilanga.enums;

/**
 * Vivacité du matériel installé sur une parcelle.
 *
 * {@code SILENCIEUX} est plus préoccupant qu'{@code AUCUN} : la parcelle est
 * réputée surveillée alors qu'elle ne l'est plus.
 */
public enum DeviceStatus {

    AUCUN("Aucun boîtier"),
    ACTIF("Actif"),
    SILENCIEUX("Silencieux");

    private final String label;

    DeviceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static DeviceStatus from(String value) {
        return DomainEnums.parse(DeviceStatus.class, value);
    }
}
