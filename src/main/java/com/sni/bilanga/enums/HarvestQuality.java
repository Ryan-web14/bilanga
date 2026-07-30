package com.sni.bilanga.enums;

/**
 * Qualité constatée d'une récolte.
 *
 * <p>Portée par une énumération plutôt que laissée en texte libre : c'est un
 * critère de comparaison entre campagnes, et « bonne », « Bonne » et « correcte »
 * rendraient toute comparaison impossible.
 */
public enum HarvestQuality {

    EXCELLENTE("Excellente"),
    BONNE("Bonne"),
    MOYENNE("Moyenne"),
    MEDIOCRE("Médiocre");

    private final String label;

    HarvestQuality(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static HarvestQuality from(String value) {
        return DomainEnums.parse(HarvestQuality.class, value);
    }
}
