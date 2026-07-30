package com.sni.bilanga.enums;

/**
 * Verdict porté sur la fiabilité des sondes d'un boîtier.
 *
 * <p>À ne pas confondre avec {@code EquipmentStatus}, qui dit si le boîtier est
 * en service ou retiré du parc : un boîtier peut être parfaitement ACTIVE et
 * remonter des mesures fausses. C'est même le cas le plus dangereux, parce que
 * rien ne le signale.
 */
public enum SensorHealth {

    SAINE("Saine", "Les mesures sont cohérentes."),

    /**
     * Doute fondé, mais insuffisant pour écarter les mesures.
     *
     * Le diagnostic continue de s'exécuter — renoncer sur un simple soupçon
     * priverait l'exploitant de conseils probablement justes — mais le résultat
     * porte une réserve explicite.
     */
    SUSPECTE("Suspecte", "Les mesures s'écartent de ce que relèvent les autres boîtiers."),

    /**
     * Panne établie. Le diagnostic est <strong>inhibé</strong> : mieux vaut ne
     * rien conseiller que conseiller à partir d'une mesure dont on sait qu'elle
     * est fausse.
     */
    DEFAILLANTE("Défaillante", "Les mesures ne sont plus exploitables.");

    private final String label;
    private final String explanation;

    SensorHealth(String label, String explanation) {
        this.label = label;
        this.explanation = explanation;
    }

    public String getLabel() {
        return label;
    }

    public String getExplanation() {
        return explanation;
    }

    /** Vrai si les mesures ne doivent plus nourrir de diagnostic. */
    public boolean blocksDiagnosis() {
        return this == DEFAILLANTE;
    }

    /** Vrai si le diagnostic reste possible mais doit porter une réserve. */
    public boolean warrantsCaution() {
        return this == SUSPECTE;
    }

    public static SensorHealth from(String value) {
        return DomainEnums.parse(SensorHealth.class, value);
    }

    /** Le pire des deux verdicts : une sonde défaillante prime sur une sonde suspecte. */
    public static SensorHealth worst(SensorHealth a, SensorHealth b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
