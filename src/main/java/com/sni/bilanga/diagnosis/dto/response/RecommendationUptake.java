package com.sni.bilanga.diagnosis.dto.response;

import com.sni.bilanga.utils.json.CounterSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Taux d'application des conseils, par moteur d'origine.
 *
 * C'est la mesure la plus directe de la pertinence du système expert : un type
 * de règle systématiquement ignoré est une règle à revoir, pas un exploitant
 * négligent.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RecommendationUptake {

    private String recommendationType;
    private String recommendationTypeLabel;

    @JsonSerialize(using = CounterSerializer.class)
    private Long total;

    @JsonSerialize(using = CounterSerializer.class)
    private Long applied;

    @JsonSerialize(using = CounterSerializer.class)
    private Long ignored;

    /** Conseils encore sans réponse. */
    @JsonSerialize(using = CounterSerializer.class)
    private Long pending;

    /**
     * Part des conseils tranchés qui ont été appliqués, entre 0 et 1.
     * {@code null} tant qu'aucun conseil de ce type n'a reçu de réponse —
     * un taux calculé sur zéro réponse serait trompeur.
     */
    private Double applicationRate;
}
