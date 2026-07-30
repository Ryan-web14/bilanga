package com.sni.bilanga.enums;

/**
 * État administratif d'un boîtier ou d'un capteur.
 *
 * À ne pas confondre avec {@link DeviceStatus}, qui décrit la <em>vivacité</em>
 * observée du matériel (actif, silencieux). Un boîtier peut être déclaré
 * {@code ACTIVE} et pourtant muet depuis trois jours — c'est précisément le
 * genre de situation que le tableau de bord doit faire ressortir.
 */
public enum EquipmentStatus {

    ACTIVE("En service"),
    RETIRE("Retiré du parc");

    private final String label;

    EquipmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static EquipmentStatus from(String value) {
        return DomainEnums.parse(EquipmentStatus.class, value);
    }
}
