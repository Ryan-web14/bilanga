package com.sni.bilanga.enums;

/**
 * Nature d'une entrée de la chronologie d'une parcelle.
 *
 * <p>Sert aussi de filtre : {@code ?types=ALERTE,INTERVENTION} évite d'interroger
 * les sources dont on n'a pas besoin. Chaque source coûte une requête ; les
 * énumérer permet de ne payer que ce qui sera affiché.
 */
public enum TimelineEventType {

    /**
     * Relevé <em>marquant</em> seulement — anomalie matérielle constatée.
     *
     * Une parcelle instrumentée produit des milliers de relevés nominaux : les
     * verser tous dans la chronologie la rendrait illisible, et masquerait
     * précisément ce qu'on y cherche.
     */
    RELEVE("Relevé"),

    DIAGNOSTIC("Diagnostic"),

    ALERTE("Alerte"),

    /** Constat terrain saisi par un humain. */
    OBSERVATION("Observation"),

    /**
     * Changement de stade de croissance.
     *
     * Reconstitué depuis la date de plantation : le stade est une colonne
     * écrasée, jamais un journal, mais c'est une fonction déterministe du temps.
     */
    STADE("Changement de stade"),

    /** Action menée au champ : irrigation, traitement, fertilisation. */
    INTERVENTION("Intervention"),

    /** Récolte enregistrée. */
    RECOLTE("Récolte");

    private final String label;

    TimelineEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static TimelineEventType from(String value) {
        return DomainEnums.parse(TimelineEventType.class, value);
    }
}
