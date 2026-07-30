package com.sni.bilanga.enums;

/**
 * Cycle de vie d'un envoi.
 *
 * {@code ECHOUEE} est réversible — la reprise la retentera ; {@code ABANDONNEE}
 * ne l'est pas : au-delà d'un certain nombre de tentatives, insister ne fait
 * qu'entretenir du bruit sur une adresse qui ne répondra pas.
 */
public enum NotificationStatus {

    EN_ATTENTE("En attente"),
    ENVOYEE("Envoyée"),
    ECHOUEE("En échec"),
    ABANDONNEE("Abandonnée");

    private final String label;

    NotificationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Vrai tant qu'une nouvelle tentative a du sens. */
    public boolean isRetryable() {
        return this == EN_ATTENTE || this == ECHOUEE;
    }

    public static NotificationStatus from(String value) {
        return DomainEnums.parse(NotificationStatus.class, value);
    }
}
