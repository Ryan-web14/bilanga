package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.harvest.dto.response.PlotEconomics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fige un bilan économique, et sait dire en quoi il a divergé depuis.
 *
 * <h2>Pourquoi cela ne viole pas le contrat « rien n'est stocké »</h2>
 *
 * <p>{@code MarginCalculator} et l'en-tête de {@code V21__harvest.sql} posent que les
 * totaux se recalculent toujours, au motif — juste — qu'« un total mis en cache diverge
 * dès la première correction de saisie, et personne ne sait plus lequel des deux
 * chiffres croire ».
 *
 * <p>Ce raisonnement est préservé par trois conditions, et c'est la troisième qui
 * compte le plus :
 *
 * <ol>
 *   <li>l'instantané est écrit <strong>une seule fois</strong>, à la clôture, et jamais
 *       rafraîchi — aucune route ne le permet ;</li>
 *   <li>il est daté et attribué, donc étiqueté « arrêté au JJ/MM » et non « le
 *       bilan » ;</li>
 *   <li><strong>la divergence est calculée et rendue avec lui.</strong> Le client voit
 *       les deux chiffres et l'écart expliqué en français.</li>
 * </ol>
 *
 * <p>Personne ne se demande donc lequel croire : les deux sont là, datés, et l'écart
 * <em>devient</em> le signal d'audit. « Le bilan arrêté le 14/03 indiquait 412 000 XAF.
 * Recalculé aujourd'hui : 388 500 XAF. » — c'est cette phrase qui transforme la tension
 * du contrat en information utile.
 *
 * <p>Le cas concret qu'elle attrape : {@code harvestRepository.delete()} est une
 * suppression <strong>réelle</strong> (assumée, javadoc explicite). Une récolte
 * supprimée après clôture rend le bilan figé faux — et la ligne de divergence est
 * exactement ce qui le rend visible.
 *
 * <p>Sans état ni transaction.
 */
@Component
@RequiredArgsConstructor
public class EconomicsFreezer {

    private static final Locale FR = Locale.FRANCE;

    /** Portée du bilan. Voir {@link #freeze} pour la raison de sa présence. */
    public static final String SCOPE_PLOT = "PARCELLE";

    /**
     * Champs comparés pour détecter une divergence.
     *
     * <p>Volontairement restreint aux <strong>montants et aux volumes</strong> : ce sont
     * les seuls chiffres qu'un exploitant lit comme un résultat. Comparer
     * {@code summary} ou {@code generatedAt} signalerait un écart à chaque appel, et le
     * signal se noierait.
     */
    private static final List<String> COMPARED = List.of(
            "grossRevenue", "totalCost", "margin", "totalQuantity",
            "harvestCount", "interventionCount");

    private static final Map<String, String> LABELS = Map.of(
            "grossRevenue", "le produit brut",
            "totalCost", "les charges",
            "margin", "la marge",
            "totalQuantity", "la quantité récoltée",
            "harvestCount", "le nombre de récoltes",
            "interventionCount", "le nombre d'interventions");

    private final ObjectMapper objectMapper;

    /**
     * Convertit un bilan vivant en charge utile figeable.
     *
     * <p><strong>{@code convertValue} vers une carte, et non une sérialisation en
     * chaîne.</strong> La colonne est un {@code jsonb} : Hibernate en attend une
     * structure, pas du texte. Le passage par l'{@code ObjectMapper} du projet garantit
     * en outre que les identifiants Snowflake sortent en <strong>chaînes</strong>, comme
     * partout ailleurs dans l'API — {@code JacksonConfig} enregistre
     * {@code ToStringSerializer} pour {@code Long}.
     *
     * <p>⚠️ <strong>Ne jamais re-désérialiser cette carte en {@code PlotEconomics}.</strong>
     * La conversion est à sens unique : les identifiants y sont devenus des chaînes, et
     * un aller-retour les rendrait incohérents. Le chemin de lecture réémet la carte
     * telle quelle.
     *
     * <h3>Pourquoi {@code scope} et {@code zoneId}, alors que le zonage n'existe pas</h3>
     *
     * <p>C'est la seule décision de cette classe qu'aucune migration future ne pourrait
     * rattraper. Le jour où une parcelle sera divisée en zones, il y aura des bilans
     * figés de parcelle et des bilans figés de zone — et <strong>rien dans la charge
     * utile ne les distinguera</strong> si le discriminant n'a pas été écrit dès le
     * premier jour. On ne peut pas déduire après coup une portée qui n'a jamais été
     * enregistrée. Coût aujourd'hui : deux clés.
     */
    public Map<String, Object> freeze(PlotEconomics economics) {
        if (economics == null) {
            return Map.of();
        }

        Map<String, Object> payload = new LinkedHashMap<>(
                objectMapper.convertValue(economics, Map.class));

        payload.put("scope", SCOPE_PLOT);
        payload.put("zoneId", null);

        return payload;
    }

    /**
     * Ce qui a changé entre le bilan figé et le bilan recalculé aujourd'hui.
     *
     * <p>Rend une <strong>phrase explicite</strong> quand rien n'a bougé, jamais une
     * liste vide : « identique » est une information rassurante, et un tableau vide
     * obligerait le client à l'interpréter — au risque d'afficher un blanc là où il faut
     * lire « rien n'a été modifié depuis la clôture ».
     */
    public Divergence divergence(Map<String, Object> frozen, PlotEconomics current) {
        if (frozen == null || frozen.isEmpty()) {
            return new Divergence(false, List.of(),
                    "Aucun bilan n'a été arrêté à la clôture de cette campagne : "
                    + "elle a été close avant que le système ne fige un bilan.");
        }
        if (current == null) {
            return new Divergence(false, List.of(),
                    "Le bilan actuel n'a pas pu être recalculé : la comparaison n'est "
                    + "pas disponible.");
        }

        Map<String, Object> now = new LinkedHashMap<>(
                objectMapper.convertValue(current, Map.class));

        List<String> changes = new ArrayList<>();
        for (String field : COMPARED) {
            String before = asText(frozen.get(field));
            String after = asText(now.get(field));

            if (before == null && after == null) {
                continue;
            }
            if (!numericallyEqual(before, after)) {
                changes.add(String.format(FR, "%s est passé de %s à %s",
                        LABELS.getOrDefault(field, field),
                        before == null ? "non renseigné" : before,
                        after == null ? "non renseigné" : after));
            }
        }

        if (changes.isEmpty()) {
            return new Divergence(false, List.of(),
                    "Le bilan recalculé aujourd'hui est identique à celui arrêté à la "
                    + "clôture : rien n'a été saisi ni corrigé depuis.");
        }

        return new Divergence(true, changes, String.format(FR,
                "Le bilan a divergé depuis la clôture : %s. Le chiffre arrêté reste la "
                        + "référence de la campagne ; l'écart signale des saisies ou des "
                        + "corrections postérieures : une récolte ajoutée, une intervention "
                        + "renseignée, ou une ligne supprimée.",
                String.join(" ; ", changes)));
    }

    /**
     * @param diverged  vrai si au moins un montant ou volume a changé
     * @param changes   les écarts, un par ligne, rédigés
     * @param statement formulation complète, prête à afficher — <strong>jamais nulle</strong>
     */
    public record Divergence(boolean diverged, List<String> changes, String statement) {
    }

    // ============================================================
    // Comparaison
    // ============================================================

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Compare numériquement quand c'est possible, textuellement sinon.
     *
     * <p>Nécessaire parce que les deux côtés ne viennent pas du même chemin : le figé a
     * fait un aller-retour par {@code jsonb} et peut porter {@code "412000.00"} là où le
     * vivant porte {@code 412000.0}. Une comparaison de chaînes signalerait alors une
     * divergence qui n'existe pas — et un faux positif sur un écran d'audit est pire
     * qu'un silence : il apprend à ignorer le signal.
     */
    private boolean numericallyEqual(String before, String after) {
        if (before == null || after == null) {
            return false;
        }
        try {
            return new BigDecimal(before).compareTo(new BigDecimal(after)) == 0;
        } catch (NumberFormatException notANumber) {
            return before.equals(after);
        }
    }
}
