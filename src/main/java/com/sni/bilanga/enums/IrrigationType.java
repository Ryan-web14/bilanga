package com.sni.bilanga.enums;

/**
 * Moyen d'irrigation dont dispose la parcelle.
 *
 * <p><strong>Ce n'est pas un champ descriptif.</strong> Le moteur agronomique
 * conseille « irriguer » dès que l'humidité du sol passe sous le seuil de la
 * culture, sans savoir si l'exploitant en a les moyens. Sur une parcelle
 * {@link #PLUVIAL}, ce conseil est inapplicable — et un conseil inapplicable est
 * précisément ce qui fait qu'un exploitant cesse de lire les recommandations.
 *
 * <p>{@code IrrigationAdapter} s'appuie sur cette valeur pour reformuler le
 * conseil en action réalisable (pailler, ombrer) plutôt que de le supprimer :
 * le constat de stress hydrique reste vrai, seule la réponse change.
 */
public enum IrrigationType {

    /** Aucune irrigation : la parcelle dépend de la pluie. */
    PLUVIAL("Pluvial", false),

    GOUTTE_A_GOUTTE("Goutte-à-goutte", true),

    ASPERSION("Aspersion", true),

    /** Arrosage à la main ou au tuyau : possible, mais coûteux en temps. */
    MANUEL("Manuel", true);

    private final String label;

    /** Faux si aucun apport d'eau n'est possible sur commande. */
    private final boolean waterOnDemand;

    IrrigationType(String label, boolean waterOnDemand) {
        this.label = label;
        this.waterOnDemand = waterOnDemand;
    }

    public String getLabel() {
        return label;
    }

    public boolean isWaterOnDemand() {
        return waterOnDemand;
    }

    /** Tolérante à la casse et aux espaces ; {@code null} si la valeur est inconnue. */
    public static IrrigationType from(String value) {
        return DomainEnums.parse(IrrigationType.class, value);
    }

    /**
     * Vrai lorsqu'on sait que la parcelle ne peut pas être irriguée.
     *
     * Une valeur absente n'est <em>pas</em> traitée comme pluviale : en
     * l'absence d'information, mieux vaut laisser le conseil d'origine que
     * le réécrire sur une hypothèse.
     */
    public static boolean cannotIrrigate(String stored) {
        IrrigationType type = from(stored);
        return type != null && !type.isWaterOnDemand();
    }
}
