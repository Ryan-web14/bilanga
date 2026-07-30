package com.sni.bilanga.knowledge.service.support;

import com.sni.bilanga.enums.IrrigationType;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rend les conseils d'arrosage applicables à la parcelle qui les reçoit.
 *
 * <p><strong>Le défaut corrigé.</strong> Le moteur agronomique conseille
 * « irriguez » dès que l'humidité du sol passe sous le seuil de la culture. Il
 * ignore si la parcelle dispose d'un moyen d'irrigation. Sur une parcelle
 * <em>pluviale</em> — le cas dominant dans l'agriculture congolaise — ce conseil
 * est inapplicable. Et un conseil inapplicable ne se contente pas d'être
 * inutile : il apprend à l'exploitant que le système ne connaît pas sa réalité,
 * et c'est ainsi qu'il cesse de lire les recommandations, y compris les bonnes.
 *
 * <p><strong>Reformuler, et non supprimer.</strong> Le constat reste vrai : le
 * sol manque d'eau. Seule la réponse doit changer — retenir l'eau plutôt qu'en
 * apporter. Effacer le conseil ferait disparaître le problème avec lui, ce qui
 * est pire que de proposer une action irréalisable.
 *
 * <p>La traçabilité ({@code measureField}, {@code observedValue},
 * {@code thresholdValue}, {@code sourceRuleId}) est <strong>conservée
 * intacte</strong> : la justification produite par {@code DiagnosisExplainer}
 * doit continuer de désigner la mesure qui a déclenché le conseil, quelle qu'en
 * soit la formulation finale.
 */
@Component
public class IrrigationAdapter {

    /**
     * Catégories dont les conseils supposent un apport d'eau sur commande.
     * {@code EXCES_EAU} n'en fait pas partie : évacuer l'eau ne demande pas
     * d'irrigation.
     */
    private static final Set<String> WATER_DEPENDENT = Set.of("STRESS_HYDRIQUE");

    /**
     * Marqueurs d'une action d'arrosage dans le texte du conseil.
     *
     * Le rattachement se fait sur la catégorie <em>et</em> sur le libellé : une
     * même catégorie porte aussi des conseils compatibles avec le pluvial
     * (pailler, ombrer), qu'il serait absurde de réécrire.
     */
    private static final List<String> WATERING_MARKERS =
            List.of("irrig", "arros", "apport d'eau", "apporter de l'eau");

    /** Substitut proposé lorsque l'eau ne peut pas être apportée sur commande. */
    private static final String RAINFED_SUBSTITUTE =
            " Cette parcelle étant en culture pluviale, l'irrigation n'est pas une option : "
            + "paillez le sol pour limiter l'évaporation, ombrez les jeunes plants aux heures "
            + "les plus chaudes, et binez en surface pour rompre la remontée capillaire. "
            + "Si le déficit persiste, l'ajustement se joue au calendrier de semis de la "
            + "campagne suivante, pas sur celle-ci.";

    /**
     * @param plot  parcelle concernée ; {@code null} laisse les conseils inchangés
     * @param items conseils assemblés par les moteurs
     * @return la même liste, les conseils d'arrosage reformulés le cas échéant
     */
    public List<RecommendationItem> adapt(Plot plot, List<RecommendationItem> items) {
        if (plot == null || items == null || items.isEmpty()) {
            return items;
        }

        // Ne rien savoir du moyen d'irrigation n'autorise pas à supposer qu'il
        // n'y en a pas : IrrigationType.cannotIrrigate ne répond vrai que sur une
        // valeur explicitement pluviale.
        if (!IrrigationType.cannotIrrigate(plot.getIrrigationType())) {
            return items;
        }

        return items.stream().map(this::rewriteIfWatering).toList();
    }

    private RecommendationItem rewriteIfWatering(RecommendationItem item) {
        if (!mentionsWatering(item)) {
            return item;
        }

        // Copie : les items viennent de moteurs qui peuvent les réutiliser, et
        // muter l'original ferait dépendre le résultat de l'ordre d'appel.
        return RecommendationItem.builder()
                .content(item.getContent() + RAINFED_SUBSTITUTE)
                .type(item.getType())
                .priority(item.getPriority())
                .category(item.getCategory())
                .sourceRuleId(item.getSourceRuleId())
                .estimatedCost(item.getEstimatedCost())
                .measureField(item.getMeasureField())
                .observedValue(item.getObservedValue())
                .thresholdValue(item.getThresholdValue())
                .build();
    }

    private boolean mentionsWatering(RecommendationItem item) {
        if (item.getContent() == null) {
            return false;
        }
        if (item.getCategory() != null && !WATER_DEPENDENT.contains(item.getCategory())) {
            return false;
        }
        String content = item.getContent().toLowerCase(Locale.FRANCE);
        return WATERING_MARKERS.stream().anyMatch(content::contains);
    }
}
