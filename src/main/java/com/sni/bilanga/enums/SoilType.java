package com.sni.bilanga.enums;

/**
 * Nature du sol d'une parcelle.
 *
 * La colonne reste en {@code VARCHAR} : ces valeurs sont le vocabulaire autorisé,
 * verrouillé côté base par une contrainte {@code CHECK} et côté API par le typage
 * des DTO de requête.
 */
public enum SoilType {

    ARGILEUX("Argileux"),
    LIMONEUX("Limoneux"),
    SABLEUX("Sableux");

    private final String label;

    SoilType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Tolérante à la casse et aux espaces ; {@code null} si la valeur est inconnue. */
    public static SoilType from(String value) {
        return DomainEnums.parse(SoilType.class, value);
    }
}
