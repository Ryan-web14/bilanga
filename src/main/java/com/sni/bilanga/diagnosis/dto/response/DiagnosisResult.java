package com.sni.bilanga.diagnosis.dto.response;


import com.sni.bilanga.knowledge.dto.response.DiseaseRisk;
import com.sni.bilanga.knowledge.dto.response.IndicatorSet;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.knowledge.dto.response.TrendFinding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DiagnosisResult {
    private Long diagnosticId;
    private String source;
    private String result;

    /** Nom français du résultat. Le modèle rend une classe anglaise ; tout le
     *  reste du diagnostic, conseils compris, est en français. */
    private String resultLabel;
    private Double confidenceScore;
    private String cropName;
    private String confidenceLevel;
    private Boolean reliable;
    private List<ClassProbability> alternatives;

    /**
     * Pourquoi la maladie retenue plutôt que les alternatives.
     *
     * <p>Croise la probabilité du modèle avec les conditions d'apparition
     * calculées sur les mesures — deux voies sans aucune information en commun.
     * Répondre à « pourquoi pas l'autre ? » est le propre d'un système
     * explicable, par opposition à un classifieur qui rend un verdict.
     *
     * <p>Vide sur la chaîne capteur, qui ne produit pas d'alternatives.
     */
    private List<AlternativeComparison> comparison;
    private String advisory;
    private String corroboration;

    /**
     * Réserve sur la qualité des données d'entrée, lorsque le relevé provient
     * d'un boîtier dont les sondes s'écartent de leurs voisines.
     *
     * <p>Distinct de {@link #confidenceLevel} : la confiance mesure la certitude
     * du modèle, jamais la fiabilité de la mesure qui l'a nourri. Un modèle peut
     * être catégorique sur une entrée fausse — c'est même le cas dangereux.
     */
    private String dataQualityNote;
    private Boolean cropAutoResolved;
    private Boolean readingAutoResolved;
    private IndicatorSet indicators;
    private List<DiseaseRisk> risks;
    private List<RecommendationItem> recommendations;
    private List<TrendFinding> trends;
}

