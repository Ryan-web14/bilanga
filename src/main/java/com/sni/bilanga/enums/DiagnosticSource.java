package com.sni.bilanga.enums;

/**
 * Voie ayant produit un diagnostic. Les deux chaînes sont indépendantes :
 * l'image porte un symptôme visible, le capteur porte des conditions mesurées.
 */
public enum DiagnosticSource {

    IMAGE("Analyse d'image"),
    CAPTEUR("Relevé de capteurs");

    private final String label;

    DiagnosticSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static DiagnosticSource from(String value) {
        return DomainEnums.parse(DiagnosticSource.class, value);
    }
}
