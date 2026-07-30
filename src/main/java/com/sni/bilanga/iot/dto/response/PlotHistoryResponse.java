package com.sni.bilanga.iot.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Série temporelle agrégée d'une parcelle.
 *
 * <p><strong>Ce que cela remplace.</strong> Pour tracer une courbe sur un mois,
 * le frontend devait rapatrier tous les relevés bruts — plusieurs milliers pour
 * une parcelle instrumentée — puis les regrouper lui-même. L'agrégation
 * appartient à la base : elle sait le faire en une requête, et ne transmet que
 * ce qui sera affiché.
 *
 * <p>Chaque point porte min, moyenne et max. La moyenne seule masquerait les
 * pics, or c'est précisément un pic de température ou un creux d'humidité qui
 * explique un diagnostic.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PlotHistoryResponse {

    private Long plotId;
    private String plotName;

    private Instant from;
    private Instant to;

    private String granularity;
    private String granularityLabel;

    /** Nombre d'intervalles retournés. */
    private Integer bucketCount;

    /** Nombre total de relevés agrégés, tous intervalles confondus. */
    private Integer readingCount;

    private List<HistoryPoint> points;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class HistoryPoint {

        /** Début de l'intervalle. */
        private Instant bucket;

        private Integer sampleCount;

        /** Relevés marqués comme physiquement impossibles dans cet intervalle. */
        private Integer anomalyCount;

        /**
         * Statistiques par mesure : {@code temperature}, {@code humidite_sol},
         * {@code humidite_air}, {@code ph}, {@code azote}, {@code phosphore},
         * {@code potassium}, {@code luminosite}. Une mesure jamais renseignée
         * sur l'intervalle est absente de la carte plutôt que présente à zéro —
         * « pas de donnée » et « zéro » ne se confondent pas.
         */
        private Map<String, MeasureStats> measures;
    }

    /**
     * @param min valeur la plus basse de l'intervalle
     * @param avg moyenne
     * @param max valeur la plus haute
     */
    public record MeasureStats(Double min, Double avg, Double max) {
    }
}
