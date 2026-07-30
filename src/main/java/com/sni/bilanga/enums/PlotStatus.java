package com.sni.bilanga.enums;

/**
 * État d'une parcelle. La suppression est logique : une parcelle archivée
 * conserve son historique de relevés et de diagnostics.
 */
public enum PlotStatus {

    ACTIVE("Active"),
    ARCHIVEE("Archivée");

    private final String label;

    PlotStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static PlotStatus from(String value) {
        return DomainEnums.parse(PlotStatus.class, value);
    }
}
