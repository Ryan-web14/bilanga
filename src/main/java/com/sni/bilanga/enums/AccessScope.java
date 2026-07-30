package com.sni.bilanga.enums;

/**
 * Domaine de données auquel un accès est demandé.
 *
 * <p>Introduit avec l'organisation en exploitations : tous les membres d'une
 * exploitation ne doivent pas voir la même chose. Un technicien répare une
 * sonde ; il n'a aucune raison de connaître les marges. Un ouvrier travaille au
 * champ ; le résultat financier ne le concerne pas dans son travail.
 *
 * <p>Ces distinctions ne s'appliquent qu'aux <strong>membres</strong> d'une
 * exploitation. Le propriétaire direct d'une parcelle voit tout, sans condition
 * — l'organisation élargit l'accès, elle ne le restreint jamais.
 */
public enum AccessScope {

    /** Diagnostics, recommandations, alertes agronomiques, relevés. */
    AGRONOMIQUE("Données agronomiques"),

    /** Marges, coûts, prix de vente, récoltes. */
    ECONOMIQUE("Données économiques"),

    /** Boîtiers, sondes, santé du matériel, alertes techniques. */
    TECHNIQUE("Données techniques");

    private final String label;

    AccessScope(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
