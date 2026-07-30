package com.sni.bilanga.overview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Synthèse de l'exploitation, tous plots confondus.
 *
 * <p>{@code PlotSummary} donnait déjà une ligne par parcelle ; il manquait le
 * niveau au-dessus — celui qu'on regarde le matin pour savoir où aller.
 * Répondre à « lequel de mes quarante membres a un problème aujourd'hui ? »
 * obligeait à parcourir la liste page par page.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class FarmOverview {

    private Integer plotCount;

    /** Répartition des parcelles par statut global, clés de {@code OverallStatus}. */
    private Map<String, Integer> plotsByStatus;

    /** Parcelles appelant une action, du plus urgent au moins urgent. */
    private List<AttentionItem> plotsNeedingAttention;

    private Integer openAlertCount;

    /** Alertes ouvertes par niveau, clés de {@code AlertLevel}. */
    private Map<String, Integer> openAlertsByLevel;

    private Integer deviceCount;

    /** Boîtiers dont la charge est passée sous le seuil de vigilance. */
    private Integer lowBatteryDeviceCount;

    /** Parcelles sans le moindre relevé : instrumentation à vérifier. */
    private Integer plotsWithoutReading;

    private Instant generatedAt;

    /** Résumé en français, directement affichable. */
    private String summary;

    /**
     * Ce que cette vue ne dit pas.
     *
     * Le statut d'une parcelle est ici déduit d'agrégats — alertes ouvertes et
     * dernier diagnostic. La vigilance fondée sur un <em>risque</em> élevé,
     * elle, suppose d'exécuter le moteur de risque sur le dernier relevé de
     * chaque parcelle : c'est ce que fait la vue détaillée, au prix d'un calcul
     * par parcelle. Une parcelle peut donc apparaître NORMAL ici et VIGILANCE
     * dans son détail.
     */
    private String limitation;

    /** Parcelle à regarder, avec le strict nécessaire pour décider d'y aller. */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class AttentionItem {
        private Long plotId;
        private String plotName;
        private String overallStatus;
        private Integer openAlertCount;
        private Instant lastReadingAt;
    }
}
