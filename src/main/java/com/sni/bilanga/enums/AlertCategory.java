package com.sni.bilanga.enums;

/**
 * Nature d'une alerte, qui détermine à qui elle s'adresse.
 *
 * <p>Les deux catégories n'appellent ni le même interlocuteur ni le même délai.
 * Les mêler dans une seule liste conduit chacun à filtrer celles de l'autre —
 * et, à force, à ne plus lire les siennes non plus.
 */
public enum AlertCategory {

    /** Situation de culture : maladie, carence, stress. S'adresse à l'exploitant. */
    AGRONOMIQUE("Agronomique"),

    /** Panne de matériel : sonde figée, boîtier muet. S'adresse au technicien. */
    TECHNIQUE("Technique");

    private final String label;

    AlertCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AlertCategory from(String value) {
        return DomainEnums.parse(AlertCategory.class, value);
    }
}
