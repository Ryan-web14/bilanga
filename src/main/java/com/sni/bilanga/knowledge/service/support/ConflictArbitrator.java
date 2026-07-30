package com.sni.bilanga.knowledge.service.support;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.knowledge.model.RecommendationArbitration;
import com.sni.bilanga.knowledge.repository.RecommendationArbitrationRepository;
import com.sni.bilanga.enums.RecommendationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Concilie les conseils issus de domaines qui se contrarient à la lecture.
 *
 * <p>Deux recommandations peuvent être justes chacune de son côté et pourtant dérouter
 * mises côte à côte : réduire l'humidité pour contenir une maladie foliaire, irriguer pour
 * lever un stress hydrique. L'une vise l'air, l'autre le sol, et la contradiction n'est
 * qu'apparente.
 *
 * <p>Le moteur ne supprime aucun conseil : il <strong>ajoute</strong> la synthèse qu'un
 * agronome formulerait, laquelle indique comment appliquer les deux ensemble.
 *
 * <h2>Ce qui a changé, et pourquoi</h2>
 *
 * <p>L'arbitrage se déclenchait sur la seule <strong>coprésence de catégories</strong>. Il
 * ne lisait ni {@code observedValue}, ni {@code thresholdValue}, ni la priorité des deux
 * conseils qu'il conciliait. Trois défauts en découlaient :
 *
 * <ol>
 *   <li><strong>Un écart marginal pesait autant qu'un écart grave.</strong> Une humidité
 *       du sol à 58 % quand la culture en demande 60 produit un conseil de stress
 *       hydrique ; combiné à n'importe quel risque sanitaire, il déclenchait une synthèse
 *       rédigée comme si les deux problèmes étaient sérieux — alors que les mesures
 *       disaient le contraire de l'un des deux.</li>
 *   <li><strong>La synthèse ne portait aucune traçabilité</strong>, et c'était le seul
 *       type de conseil que {@code DiagnosisExplainer} ne savait pas justifier : il
 *       reconstruit les justifications depuis {@code measureField}, {@code observedValue}
 *       et {@code thresholdValue}, qui restaient vides.</li>
 *   <li><strong>La priorité venait de la règle</strong>, toujours {@code HAUTE} dans le
 *       jeu semé. Comme le tri place {@code ARBITRAGE} en tête à priorité égale, un
 *       conflit marginal passait devant un vrai problème isolé.</li>
 * </ol>
 *
 * <p><strong>Le défaut n'était pas d'ajouter, mais d'ajouter trop tôt.</strong> Corriger le
 * déclencheur n'est pas retirer un avis : l'invariant « on reformule, on n'efface pas »
 * reste entier.
 *
 * <p>Sans état ni transaction.
 */
@Component
@RequiredArgsConstructor
public class ConflictArbitrator {

    private static final Locale FR = Locale.FRANCE;

    private final RecommendationArbitrationRepository arbitrationRepository;
    private final BilangaProperties.Arbitration config;

    public List<RecommendationItem> arbitrate(String cropName, List<RecommendationItem> items) {
        List<RecommendationItem> syntheses = new ArrayList<>();
        if (cropName == null || items == null || items.size() < 2) {
            return syntheses;
        }

        for (RecommendationArbitration rule : arbitrationRepository.findForCrop(cropName)) {
            // On retient l'item le PLUS significatif de chaque catégorie : si plusieurs
            // conseils partagent une catégorie, c'est le plus marqué qui décide si le
            // conflit mérite d'être arbitré.
            Optional<RecommendationItem> sideA = mostSignificant(items, rule.getCategoryA());
            Optional<RecommendationItem> sideB = mostSignificant(items, rule.getCategoryB());

            if (sideA.isEmpty() || sideB.isEmpty()) {
                continue;
            }
            if (!bothSignificant(sideA.get(), sideB.get())) {
                continue;
            }
            syntheses.add(synthesise(rule, sideA.get(), sideB.get()));
        }
        return syntheses;
    }

    // ============================================================
    // Le filtre
    // ============================================================

    /**
     * Un conflit n'existe que si <strong>les deux</strong> problèmes existent vraiment.
     *
     * <p>Concilier un problème sérieux avec un écart d'un pour cent n'est pas un
     * arbitrage : c'est du bruit qui s'affiche en tête de liste.
     *
     * <p>Un item dont on ne peut pas mesurer l'écart — pas de seuil, pas de valeur — passe
     * le filtre. Il vaut mieux une synthèse de trop qu'une synthèse perdue faute de savoir
     * juger : les moteurs de risque et de tendance ne renseignent pas tous ces colonnes.
     */
    private boolean bothSignificant(RecommendationItem a, RecommendationItem b) {
        if (!config.isRequireSignificantDeviation()) {
            return true;
        }
        return isSignificant(a) && isSignificant(b);
    }

    private boolean isSignificant(RecommendationItem item) {
        Double deviation = relativeDeviation(item);

        // Écart non mesurable : on ne bloque pas sur une ignorance.
        if (deviation == null) {
            return true;
        }
        return deviation >= config.getMinDeviation();
    }

    /**
     * Écart <strong>relatif au seuil</strong>, jamais absolu.
     *
     * <p>Un écart de 2 sur un pH est considérable, le même sur une concentration d'azote
     * est négligeable. C'est le raisonnement déjà retenu par {@code SensorHealthAnalyzer},
     * qui rapporte l'écart à l'étendue observée plutôt qu'à la valeur brute.
     *
     * <p>{@code null} quand l'écart n'est pas calculable — valeur ou seuil absent, ou
     * seuil nul, qui ferait diverger le rapport.
     */
    private Double relativeDeviation(RecommendationItem item) {
        Double observed = item.getObservedValue();
        Double threshold = item.getThresholdValue();

        if (observed == null || threshold == null || Math.abs(threshold) < 1e-9) {
            return null;
        }
        return Math.abs(observed - threshold) / Math.abs(threshold);
    }

    // ============================================================
    // La synthèse
    // ============================================================

    private RecommendationItem synthesise(RecommendationArbitration rule,
                                          RecommendationItem a, RecommendationItem b) {

        return RecommendationItem.builder()
                .content(rule.getSynthesis())
                .type(RecommendationType.ARBITRAGE.name())
                .priority(weakestOf(a, b, rule.getPriority()))
                .category(rule.getCategoryA() + "+" + rule.getCategoryB())

                // Traçabilité reportée du côté le plus marqué. Sans elle, l'arbitrage
                // était le seul conseil que /explain ne pouvait pas justifier.
                .measureField(traceField(a, b))
                .observedValue(pickObserved(a, b))
                .thresholdValue(pickThreshold(a, b))
                .build();
    }

    /**
     * La priorité du <strong>moins urgent</strong> des deux, jamais celle de la règle.
     *
     * <p>Concilier deux problèmes n'en rend aucun plus urgent. L'arbitrage s'auto-promouvait
     * en {@code HAUTE} — les quatre règles semées le sont toutes — et passait donc devant
     * un vrai problème isolé, puisque le tri le place en tête à priorité égale.
     *
     * <p>La priorité de la règle sert de repli quand aucun des deux côtés n'en porte.
     */
    private String weakestOf(RecommendationItem a, RecommendationItem b, String fallback) {
        int rankA = RecommendationPriority.rankOf(a.getPriority());
        int rankB = RecommendationPriority.rankOf(b.getPriority());

        if (rankA == RecommendationPriority.UNKNOWN_RANK
                && rankB == RecommendationPriority.UNKNOWN_RANK) {
            return fallback;
        }

        // Rang croissant = urgence décroissante : le plus GRAND rang est le moins urgent.
        int weakest = Math.max(
                rankA == RecommendationPriority.UNKNOWN_RANK ? rankB : rankA,
                rankB == RecommendationPriority.UNKNOWN_RANK ? rankA : rankB);

        return switch (weakest) {
            case 0 -> RecommendationPriority.HAUTE.name();
            case 1 -> RecommendationPriority.MOYENNE.name();
            case 2 -> RecommendationPriority.BASSE.name();
            default -> fallback;
        };
    }

    /**
     * Les deux mesures en cause, nommées ensemble.
     *
     * <p>Un arbitrage naît de <em>deux</em> écarts ; n'en citer qu'un donnerait une
     * justification qui ne rend pas compte du conflit. Le format
     * {@code humidite_sol+humidite_air} reste lisible et se scinde trivialement côté
     * client.
     */
    private String traceField(RecommendationItem a, RecommendationItem b) {
        String fieldA = a.getMeasureField();
        String fieldB = b.getMeasureField();

        if (fieldA == null) return fieldB;
        if (fieldB == null) return fieldA;
        return fieldA.equals(fieldB) ? fieldA : String.format(FR, "%s+%s", fieldA, fieldB);
    }

    /**
     * La valeur du côté le plus marqué — celui dont l'écart relatif est le plus grand.
     *
     * <p>Rendre les deux exigerait des colonnes que le schéma n'a pas. Retenir le plus
     * marqué est le choix qui explique le mieux pourquoi la synthèse a été produite.
     */
    private Double pickObserved(RecommendationItem a, RecommendationItem b) {
        return mostDeviant(a, b).getObservedValue();
    }

    private Double pickThreshold(RecommendationItem a, RecommendationItem b) {
        return mostDeviant(a, b).getThresholdValue();
    }

    private RecommendationItem mostDeviant(RecommendationItem a, RecommendationItem b) {
        Double deviationA = relativeDeviation(a);
        Double deviationB = relativeDeviation(b);

        if (deviationA == null) return b;
        if (deviationB == null) return a;
        return deviationA >= deviationB ? a : b;
    }

    // ============================================================
    // Interne
    // ============================================================

    /**
     * L'item le plus marqué d'une catégorie donnée.
     *
     * <p>Plusieurs conseils peuvent partager une catégorie — deux carences, deux risques.
     * Retenir le plus marqué évite qu'un conseil mineur empêche l'arbitrage d'un conflit
     * réel signalé par un autre conseil de la même famille.
     */
    private Optional<RecommendationItem> mostSignificant(List<RecommendationItem> items,
                                                         String category) {
        if (category == null) {
            return Optional.empty();
        }
        RecommendationItem best = null;
        Double bestDeviation = null;

        for (RecommendationItem item : items) {
            if (!category.equals(item.getCategory())) {
                continue;
            }
            Double deviation = relativeDeviation(item);

            // Un écart non mesurable l'emporte : il ne doit pas être écarté au profit
            // d'un écart connu mais plus faible, sans quoi le filtre deviendrait plus
            // strict qu'annoncé.
            if (deviation == null) {
                return Optional.of(item);
            }
            if (bestDeviation == null || deviation > bestDeviation) {
                best = item;
                bestDeviation = deviation;
            }
        }
        return Optional.ofNullable(best);
    }
}
