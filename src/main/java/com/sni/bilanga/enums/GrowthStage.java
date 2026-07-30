package com.sni.bilanga.enums;

/**
 * Stade de développement d'une culture.
 *
 * Les stades ne sont pas communs à toutes les cultures : la tomate fructifie,
 * le manioc tubérise. La table {@code crop_stage_requirement} (migration V10)
 * porte les seuils propres à chaque couple culture / stade.
 */
public enum GrowthStage {

    LEVEE("Levée"),
    CROISSANCE("Croissance"),
    FLORAISON("Floraison"),
    FRUCTIFICATION("Fructification"),
    MATURATION("Maturation"),
    TUBERISATION("Tubérisation");

    private final String label;

    GrowthStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static GrowthStage from(String value) {
        return DomainEnums.parse(GrowthStage.class, value);
    }
}
