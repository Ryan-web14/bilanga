package com.sni.bilanga.enums;

/**
 * Pas d'agrégation d'une série temporelle.
 *
 * <p>La valeur est reportée telle quelle dans {@code date_trunc} de PostgreSQL.
 * L'énumération n'est donc pas seulement une commodité de saisie : elle garantit
 * qu'aucune chaîne arbitraire ne parvient à la fonction.
 */
public enum HistoryGranularity {

    HOUR("hour", "Par heure"),
    DAY("day", "Par jour"),
    WEEK("week", "Par semaine"),
    MONTH("month", "Par mois");

    private final String sqlUnit;
    private final String label;

    HistoryGranularity(String sqlUnit, String label) {
        this.sqlUnit = sqlUnit;
        this.label = label;
    }

    /** Unité attendue par {@code date_trunc}. */
    public String getSqlUnit() {
        return sqlUnit;
    }

    public String getLabel() {
        return label;
    }

    public static HistoryGranularity from(String value) {
        return DomainEnums.parse(HistoryGranularity.class, value);
    }
}
