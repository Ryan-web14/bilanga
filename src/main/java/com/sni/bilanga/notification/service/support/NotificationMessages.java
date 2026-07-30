package com.sni.bilanga.notification.service.support;

import com.sni.bilanga.diagnosis.model.Alert;
import com.sni.bilanga.enums.AlertCategory;
import com.sni.bilanga.enums.AlertLevel;
import com.sni.bilanga.enums.NotificationLanguage;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Compose le sujet et le corps d'une notification dans la langue du destinataire.
 *
 * <h2>Le partage retenu</h2>
 *
 * <p><strong>L'enveloppe est traduite ; le constat agronomique reste en
 * français.</strong> Le texte des moteurs est une prose composée à la volée — «
 * l'humidité du sol vaut 24,00, soit en deçà du seuil de 35,00 ». La traduire
 * supposerait de traduire chaque règle de la base de connaissance, chaque libellé
 * de mesure et chaque gabarit de phrase, à trois exemplaires, en les maintenant
 * alignés à chaque évolution du moteur.
 *
 * <p>Une traduction qui dérive est <em>pire</em> qu'une absence de traduction :
 * elle donne un conseil faux dans la langue que la personne comprend le mieux,
 * donc celui qu'elle suivra. C'est la même règle que celle appliquée partout
 * ailleurs dans ce projet — mieux vaut ne rien conseiller que conseiller faux.
 *
 * <p>Ce qui est traduit est donc ce qui <strong>décide de l'action</strong> :
 * l'urgence, la parcelle, l'appel à agir. C'est aussi ce qu'on lit en premier sur
 * l'écran d'un téléphone simple, avant même d'ouvrir le message.
 *
 * <h2>Trois décisions de forme</h2>
 *
 * <p><strong>Le niveau d'abord, la parcelle ensuite.</strong> Sur un téléphone,
 * seuls les premiers caractères sont visibles dans la liste des messages : y
 * mettre le nom du service serait perdre la seule place qui compte.
 *
 * <p><strong>La catégorie est dite explicitement.</strong> Une alerte technique
 * (« la sonde est en panne ») et une alerte agronomique (« la culture est
 * menacée ») n'appellent ni la même personne ni la même urgence. Les mêler dans
 * une formulation unique ferait qu'aucune des deux ne serait prise au sérieux.
 *
 * <p><strong>Le préfixe {@code [Bilanga]} est conservé quelle que soit la
 * langue.</strong> C'est un nom propre, et c'est lui qui permet à un exploitant de
 * distinguer d'un coup d'œil ce message des dizaines d'autres que reçoit un
 * téléphone.
 *
 * <p>⚠️ <strong>Les formulations lingala et kituba sont à faire relire par un
 * locuteur natif</strong> avant toute mise en service — au même titre que les
 * seuils agronomiques semés par les migrations. Elles sont écrites pour être
 * corrigées, pas pour être crues.
 */
@Component
public class NotificationMessages {

    private static final String BRAND = "[Bilanga]";

    /**
     * Vocabulaire par langue.
     *
     * <p>Les clés sont volontairement descriptives plutôt que numérotées : une
     * clé {@code msg_017} rendrait impossible de relire la table pour vérifier
     * qu'une langue est complète — ce qui est exactement le contrôle qu'un
     * locuteur natif viendra faire.
     */
    private static final Map<NotificationLanguage, Map<String, String>> VOCABULARY = Map.of(

            NotificationLanguage.FR, Map.ofEntries(
                    Map.entry("level.CRITIQUE", "URGENT"),
                    Map.entry("level.ELEVEE", "Important"),
                    Map.entry("level.MOYENNE", "À surveiller"),
                    Map.entry("category.AGRONOMIQUE", "culture"),
                    Map.entry("category.TECHNIQUE", "matériel"),
                    Map.entry("subject.plot", "parcelle"),
                    Map.entry("lead.AGRONOMIQUE.CRITIQUE",
                            "Votre culture est menacée. Allez voir la parcelle aujourd'hui."),
                    Map.entry("lead.AGRONOMIQUE.ELEVEE",
                            "Votre culture demande attention. Allez voir la parcelle."),
                    Map.entry("lead.AGRONOMIQUE.MOYENNE",
                            "Quelque chose a changé sur votre parcelle. À surveiller."),
                    Map.entry("lead.TECHNIQUE.CRITIQUE",
                            "Le boîtier ne mesure plus correctement. Aucun conseil ne sera "
                                    + "donné tant qu'il n'est pas réparé."),
                    Map.entry("lead.TECHNIQUE.ELEVEE",
                            "Le boîtier ne mesure plus correctement. Une sonde est à vérifier."),
                    Map.entry("lead.TECHNIQUE.MOYENNE",
                            "Le boîtier mérite une vérification."),
                    Map.entry("detail.header", "Détail"),
                    Map.entry("grouped.one", "situation"),
                    Map.entry("grouped.many", "situations"),
                    Map.entry("footer",
                            "Le détail technique est en français. Votre conseiller peut vous "
                                    + "l'expliquer.")),

            // ── Lingala ─────────────────────────────────────────────
            NotificationLanguage.LN, Map.ofEntries(
                    Map.entry("level.CRITIQUE", "MBANGU"),
                    Map.entry("level.ELEVEE", "Na ntina"),
                    Map.entry("level.MOYENNE", "Kotala"),
                    Map.entry("category.AGRONOMIQUE", "milona"),
                    Map.entry("category.TECHNIQUE", "masini"),
                    Map.entry("subject.plot", "elanga"),
                    Map.entry("lead.AGRONOMIQUE.CRITIQUE",
                            "Milona na yo ezali na likama. Kende kotala elanga lelo."),
                    Map.entry("lead.AGRONOMIQUE.ELEVEE",
                            "Milona na yo esengeli na bokebi. Kende kotala elanga."),
                    Map.entry("lead.AGRONOMIQUE.MOYENNE",
                            "Eloko ebongwani na elanga na yo. Tala malamu."),
                    Map.entry("lead.TECHNIQUE.CRITIQUE",
                            "Masini ezali komeka malamu te. Toboyi kopesa toli kina "
                                    + "esengi kobongisama."),
                    Map.entry("lead.TECHNIQUE.ELEVEE",
                            "Masini ezali komeka malamu te. Esengeli kotala sonde."),
                    Map.entry("lead.TECHNIQUE.MOYENNE",
                            "Masini esengeli kotalama."),
                    Map.entry("detail.header", "Sango mobimba"),
                    Map.entry("grouped.one", "likambo"),
                    Map.entry("grouped.many", "makambo"),
                    Map.entry("footer",
                            "Sango mobimba ezali na Falansé. Molakisi na yo akoki "
                                    + "kolimbola yo yango.")),

            // ── Kituba ──────────────────────────────────────────────
            NotificationLanguage.KG, Map.ofEntries(
                    Map.entry("level.CRITIQUE", "NSWALU"),
                    Map.entry("level.ELEVEE", "Ya mfunu"),
                    Map.entry("level.MOYENNE", "Kutala"),
                    Map.entry("category.AGRONOMIQUE", "bilanga"),
                    Map.entry("category.TECHNIQUE", "masini"),
                    Map.entry("subject.plot", "kilanga"),
                    Map.entry("lead.AGRONOMIQUE.CRITIQUE",
                            "Bilanga na nge kele na mpasi. Kwenda kutala kilanga bubu."),
                    Map.entry("lead.AGRONOMIQUE.ELEVEE",
                            "Bilanga na nge kelombaka kutala. Kwenda kutala kilanga."),
                    Map.entry("lead.AGRONOMIQUE.MOYENNE",
                            "Kima mesobana na kilanga na nge. Tala mbote."),
                    Map.entry("lead.TECHNIQUE.CRITIQUE",
                            "Masini kemeka mbote ve. Beto tapesa ndongisila ve tii "
                                    + "yo tabongisama."),
                    Map.entry("lead.TECHNIQUE.ELEVEE",
                            "Masini kemeka mbote ve. Sonde kelombaka kutalama."),
                    Map.entry("lead.TECHNIQUE.MOYENNE",
                            "Masini kelombaka kutalama."),
                    Map.entry("detail.header", "Nsangu ya mvimba"),
                    Map.entry("grouped.one", "diambu"),
                    Map.entry("grouped.many", "mambu"),
                    Map.entry("footer",
                            "Nsangu ya mvimba kele na Falansa. Nlongi na nge lenda "
                                    + "tendula yo yo.")));

    // ============================================================
    // Sujet
    // ============================================================

    /**
     * Sujet court : marque, urgence, catégorie, parcelle.
     *
     * <p>Cet ordre est celui des caractères visibles dans une liste de messages sur
     * un téléphone simple. Le nom de la parcelle vient en dernier parce qu'il est le
     * plus long et le plus variable — le tronquer coûte moins que de tronquer
     * l'urgence.
     */
    public String subjectFor(Alert alert, NotificationLanguage language) {
        Map<String, String> words = wordsFor(language);

        return String.format("%s %s · %s · %s %s",
                BRAND,
                levelWord(words, alert.getLevel()),
                categoryWord(words, alert.getCategory()),
                words.get("subject.plot"),
                plotNameOf(alert));
    }

    /** Sujet d'un envoi regroupant plusieurs situations. */
    public String groupedSubjectFor(Alert alert, NotificationLanguage language, int count) {
        Map<String, String> words = wordsFor(language);
        String unit = count <= 1 ? words.get("grouped.one") : words.get("grouped.many");

        return String.format("%s (%d %s)", subjectFor(alert, language), count, unit);
    }

    // ============================================================
    // Corps
    // ============================================================

    /**
     * Corps du message : une amorce traduite, puis le constat des moteurs.
     *
     * <p>L'amorce dit <em>quoi faire</em> et dépend de la catégorie ET du niveau :
     * « allez voir aujourd'hui » et « à surveiller » n'appellent pas le même
     * déplacement, et une panne de sonde n'appelle pas le même geste qu'une menace
     * sur la culture. Neuf formulations plutôt qu'une seule, parce qu'une amorce
     * qui vaut pour tout ne dit rien.
     *
     * <p>Le pied de message n'apparaît que dans une langue autre que le français :
     * il annonce que le détail est en français et invite à s'appuyer sur son
     * conseiller. Le dire vaut mieux que de laisser croire à un message tronqué.
     */
    public String bodyFor(Alert alert, NotificationLanguage language) {
        Map<String, String> words = wordsFor(language);
        StringBuilder body = new StringBuilder();

        body.append(leadFor(words, alert));

        String detail = alert.getMessage();
        if (detail != null && !detail.isBlank()) {
            body.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append(words.get("detail.header")).append(" : ")
                    .append(detail.trim());
        }

        if (language != NotificationLanguage.FR) {
            body.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append(words.get("footer"));
        }
        return body.toString();
    }

    /**
     * Amorce d'action, choisie sur le couple catégorie × niveau.
     *
     * <p>Retombe sur l'amorce agronomique de niveau moyen si la combinaison est
     * inconnue — une catégorie non reconnue ne doit pas produire un message vide,
     * qui serait pire qu'un message imprécis.
     */
    private String leadFor(Map<String, String> words, Alert alert) {
        AlertCategory category = AlertCategory.from(alert.getCategory());
        AlertLevel level = AlertLevel.from(alert.getLevel());

        String key = "lead."
                + (category == null ? AlertCategory.AGRONOMIQUE.name() : category.name())
                + "."
                + (level == null ? AlertLevel.MOYENNE.name() : level.name());

        return words.getOrDefault(key, words.get("lead.AGRONOMIQUE.MOYENNE"));
    }

    // ============================================================
    // Interne
    // ============================================================

    private Map<String, String> wordsFor(NotificationLanguage language) {
        return VOCABULARY.getOrDefault(
                language == null ? NotificationLanguage.DEFAULT : language,
                VOCABULARY.get(NotificationLanguage.FR));
    }

    /**
     * Le niveau brut est conservé en repli plutôt que remplacé par un terme
     * générique : sur un message d'alerte, une valeur inconnue affichée telle
     * quelle se diagnostique, un « Information » fabriqué se confond avec le bruit.
     */
    private String levelWord(Map<String, String> words, String rawLevel) {
        AlertLevel level = AlertLevel.from(rawLevel);
        if (level == null) {
            return rawLevel == null ? "" : rawLevel;
        }
        return words.getOrDefault("level." + level.name(), level.name());
    }

    private String categoryWord(Map<String, String> words, String rawCategory) {
        AlertCategory category = AlertCategory.from(rawCategory);
        if (category == null) {
            category = AlertCategory.AGRONOMIQUE;
        }
        return words.getOrDefault("category." + category.name(),
                category.name().toLowerCase(Locale.ROOT));
    }

    private String plotNameOf(Alert alert) {
        if (alert.getPlot() == null || alert.getPlot().getName() == null) {
            return "?";
        }
        return alert.getPlot().getName();
    }
}
