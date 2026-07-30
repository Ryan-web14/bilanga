package com.sni.bilanga.enums;

/**
 * Nature d'une action menée au champ.
 *
 * <p>Chaque type porte la <strong>mesure qu'il est censé faire bouger</strong>.
 * C'est ce qui permet à {@code EffectAnalyzer} de juger une intervention sur le
 * bon critère : irriguer doit relever l'humidité du sol, fertiliser doit relever
 * l'azote. Sans cette correspondance, l'analyse d'effet ne pourrait que
 * constater « quelque chose a changé », ce qui ne vaut rien.
 */
public enum InterventionType {

    IRRIGATION("Irrigation", "humidite_sol", true),

    /** Vise l'azote en premier lieu — l'élément le plus mobile et le plus suivi. */
    FERTILISATION("Fertilisation", "azote", true),

    /**
     * Traitement phytosanitaire.
     *
     * Aucune mesure attendue : l'effet d'un fongicide se lit sur les diagnostics
     * suivants, pas sur une valeur de sonde. Prétendre le contraire donnerait un
     * verdict faux avec l'apparence de la rigueur.
     */
    TRAITEMENT("Traitement", null, false),

    DESHERBAGE("Désherbage", null, false),

    SEMIS("Semis", null, false),

    RECOLTE("Récolte", null, false),

    AUTRE("Autre", null, false);

    private final String label;

    /** Mesure que l'intervention est censée faire évoluer, ou {@code null}. */
    private final String targetMeasure;

    /** Vrai si l'effet attendu est une <em>hausse</em> de cette mesure. */
    private final boolean expectsIncrease;

    InterventionType(String label, String targetMeasure, boolean expectsIncrease) {
        this.label = label;
        this.targetMeasure = targetMeasure;
        this.expectsIncrease = expectsIncrease;
    }

    public String getLabel() {
        return label;
    }

    public String getTargetMeasure() {
        return targetMeasure;
    }

    public boolean isExpectsIncrease() {
        return expectsIncrease;
    }

    /**
     * Vrai lorsque l'effet peut se lire sur une mesure de sonde.
     *
     * Pour les autres, l'effet se juge sur les diagnostics qui suivent — et le
     * dire est plus honnête que de produire un chiffre sans fondement.
     */
    public boolean hasMeasurableEffect() {
        return targetMeasure != null;
    }

    public static InterventionType from(String value) {
        return DomainEnums.parse(InterventionType.class, value);
    }
}
