package com.sni.bilanga.enums;

/**
 * Famille de modèle enregistrée dans {@code ai_models}.
 *
 * {@code VISION} est décliné par culture (un modèle tomate, un modèle manioc) ;
 * {@code TABULAR} est unique et transverse — sa colonne {@code crop_name} reste nulle.
 */
public enum ModelType {

    VISION("Classification d'image"),
    TABULAR("Analyse de sol");

    private final String label;

    ModelType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ModelType from(String value) {
        return DomainEnums.parse(ModelType.class, value);
    }
}
