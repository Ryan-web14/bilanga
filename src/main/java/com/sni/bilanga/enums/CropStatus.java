package com.sni.bilanga.enums;

/**
 * État d'une plantation. Une seule culture {@code EN_COURS} par parcelle sert de
 * contexte au diagnostic : c'est elle qui détermine la culture et le stade.
 */
public enum CropStatus {

    EN_COURS("En cours"),
    TERMINEE("Terminée");

    private final String label;

    CropStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return this == EN_COURS;
    }

    public static CropStatus from(String value) {
        return DomainEnums.parse(CropStatus.class, value);
    }
}
