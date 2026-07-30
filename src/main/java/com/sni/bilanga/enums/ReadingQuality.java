package com.sni.bilanga.enums;

/**
 * Provenance d'un relevé.
 *
 * Distinguer une mesure de terrain d'une saisie manuelle ou d'une simulation
 * importe au moment d'interpréter un diagnostic : une donnée simulée ne doit
 * pas peser autant qu'une mesure réelle dans l'appréciation d'une situation.
 */
public enum ReadingQuality {

    TERRAIN("Mesure de terrain"),
    MANUELLE("Saisie manuelle"),
    SIMULEE("Simulation");

    private final String label;

    ReadingQuality(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ReadingQuality from(String value) {
        return DomainEnums.parse(ReadingQuality.class, value);
    }
}
