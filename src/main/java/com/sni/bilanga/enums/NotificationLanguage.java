package com.sni.bilanga.enums;

/**
 * Langue dans laquelle une notification est adressée à son destinataire.
 *
 * <p><strong>Pourquoi seulement les notifications.</strong> Traduire toute
 * l'interface d'administration n'aurait guère de sens : elle est utilisée par des
 * agronomes et des techniciens, dont le français est la langue de travail. Les
 * <em>notifications</em> sont un cas à part — ce sont les seuls messages que
 * l'application adresse à quelqu'un qui n'a pas choisi de la consulter, sur un
 * téléphone simple, au champ. C'est là, et seulement là, que la langue décide si
 * le message est lu ou ignoré.
 *
 * <p>Le lingala et le kituba sont les deux véhiculaires du Congo : le premier
 * domine à Brazzaville et dans le nord, le second dans le Pool, le Niari et le
 * sud. Un exploitant de Makotipoko lit couramment l'un des deux, pas
 * nécessairement le français administratif.
 *
 * <h2>Ce qui est traduit, et ce qui ne l'est pas</h2>
 *
 * <p><strong>L'enveloppe est traduite ; le constat agronomique reste en
 * français.</strong> C'est une décision, pas une paresse.
 *
 * <p>Le texte produit par les moteurs est une prose technique composée à la
 * volée — « l'humidité du sol vaut 24,00, soit en deçà du seuil de 35,00 ». La
 * traduire supposerait de traduire chaque règle, chaque libellé de mesure et
 * chaque gabarit de phrase, à trois exemplaires, en les maintenant alignés à
 * chaque évolution du moteur. Une traduction qui dérive est pire qu'une absence
 * de traduction : elle donne un conseil <em>faux</em> dans la langue que la
 * personne comprend le mieux, donc celui qu'elle suivra.
 *
 * <p>Ce qui est traduit est ce qui décide de l'action : l'urgence, la parcelle
 * concernée, et l'appel à agir. Un exploitant qui lit « Likebisa ya mbangu :
 * elanga Parcelle Nord » sait en une seconde qu'il doit se déplacer — et le
 * détail français en dessous lui dira quoi faire, ou sera lu par le conseiller
 * qu'il appellera. C'est le partage honnête entre ce qu'on sait traduire
 * fidèlement et ce qu'on ne sait pas.
 *
 * <p>⚠️ Les formulations lingala et kituba de {@code NotificationMessages} sont
 * <strong>à faire relire par un locuteur natif</strong> avant toute mise en
 * service, au même titre que les seuils agronomiques. Elles sont écrites pour
 * être corrigées, pas pour être crues.
 */
public enum NotificationLanguage {

    FR("fr", "Français"),

    /** Lingala — Brazzaville et le nord du pays. */
    LN("ln", "Lingala"),

    /** Kituba (munukutuba) — le Pool, le Niari, le sud. */
    KG("kg", "Kituba");

    /** Code stocké dans {@code notification_preference.language}. */
    private final String code;

    private final String label;

    NotificationLanguage(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Langue de repli.
     *
     * <p>Le français, et non la langue majoritaire : c'est la seule dans laquelle
     * le constat agronomique est de toute façon rédigé. Choisir un véhiculaire par
     * défaut donnerait un message dont l'enveloppe et le corps ne parlent pas la
     * même langue, sans que personne l'ait demandé.
     */
    public static final NotificationLanguage DEFAULT = FR;

    /**
     * Tolérant à la casse et aux formes régionales : {@code fr}, {@code FR},
     * {@code fr-CG} et {@code fr_CG} désignent la même langue.
     *
     * @return {@link #DEFAULT} pour une valeur absente ou inconnue — jamais
     *         {@code null}. Une langue non reconnue ne doit pas empêcher
     *         l'envoi : mieux vaut un message en français qu'aucun message.
     */
    public static NotificationLanguage from(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        int separator = normalized.indexOf('-');
        if (separator < 0) {
            separator = normalized.indexOf('_');
        }
        if (separator > 0) {
            normalized = normalized.substring(0, separator);
        }

        for (NotificationLanguage language : values()) {
            if (language.code.equals(normalized)) {
                return language;
            }
        }
        return DEFAULT;
    }

    /** Codes acceptés en entrée, pour un message d'erreur ou un menu déroulant. */
    public static java.util.List<String> acceptedCodes() {
        return java.util.Arrays.stream(values()).map(NotificationLanguage::getCode).toList();
    }
}
