package com.sni.bilanga.enums;

/**
 * Moteur ayant produit un conseil.
 *
 * <p>Le commentaire de la migration V2 n'annonçait que {@code BASE | CORRELATION} ;
 * les données montrent que quatre autres valeurs sont émises depuis. Ce sont les
 * cinq sources de recommandation assemblées par {@code DiagnosisServiceImpl},
 * plus l'arbitrage :
 *
 * <ul>
 *   <li>{@code BASE} — règle de la base de connaissance liée à la maladie diagnostiquée</li>
 *   <li>{@code AGRONOMIQUE} — écart mesuré aux exigences de la culture</li>
 *   <li>{@code RISQUE} — conditions d'apparition d'une maladie réunies</li>
 *   <li>{@code TENDANCE} — franchissement de seuil projeté</li>
 *   <li>{@code CORRELATION} — croisement image / mesures</li>
 *   <li>{@code METEO} — prévision externe (sixième moteur, migration V20)</li>
 *   <li>{@code ARBITRAGE} — synthèse de deux conseils contradictoires</li>
 * </ul>
 *
 * {@code ARBITRAGE} est à part : ce n'est pas un conseil de plus mais la
 * conciliation de deux autres. Il précède ceux qu'il concilie.
 */
public enum RecommendationType {

    BASE("Règle de la base de connaissance"),
    AGRONOMIQUE("Écart aux exigences de la culture"),
    RISQUE("Conditions favorables à une maladie"),
    TENDANCE("Évolution projetée des mesures"),
    CORRELATION("Corrélation image / mesures"),

    /**
     * Prévision météo.
     *
     * Le seul moteur qui regarde <em>devant</em> à partir d'une source externe :
     * les cinq autres raisonnent sur ce qui a été mesuré, {@code TENDANCE}
     * compris — qui extrapole les mesures internes sans rien savoir du ciel.
     */
    METEO("Prévision météo"),

    /**
     * Risque venu d'une parcelle voisine (V27).
     *
     * <p><strong>Pourquoi un type à part et non {@code RISQUE}.</strong> Les deux ne
     * se lisent pas de la même façon, et l'exploitant doit pouvoir les distinguer :
     * {@code RISQUE} dit que <em>ses</em> mesures réunissent les conditions
     * d'apparition — c'est observable et vérifiable chez lui ; {@code VOISINAGE} dit
     * qu'une maladie a été détectée <em>ailleurs</em>, et rien n'est encore visible
     * sur sa parcelle. C'est précisément l'intérêt du conseil : il est préventif.
     *
     * <p>Les confondre produirait un conseil incompréhensible — « conditions
     * favorables au mildiou » alors que les mesures locales ne le disent pas — et
     * l'exploitant chercherait l'erreur dans ses sondes.
     *
     * <p>⚠️ Toute nouvelle valeur ici exige d'étendre {@code chk_recommendations_type}
     * par une migration. Sans cela, la première recommandation de ce type fait
     * échouer son insertion <strong>au cœur du diagnostic</strong>, et fait donc
     * perdre le diagnostic entier. C'est le piège qui a failli coûter cher au rang 6.
     */
    VOISINAGE("Maladie détectée sur une parcelle voisine"),

    ARBITRAGE("Synthèse d'arbitrage");

    private final String label;

    RecommendationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static RecommendationType from(String value) {
        return DomainEnums.parse(RecommendationType.class, value);
    }

    public static boolean isArbitration(String value) {
        return ARBITRAGE == from(value);
    }
}
