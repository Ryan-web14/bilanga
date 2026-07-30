package com.sni.bilanga.overview.service.interfaces;


import com.sni.bilanga.overview.dto.response.FarmOverview;
import com.sni.bilanga.overview.dto.response.PlotOverview;
import com.sni.bilanga.overview.dto.response.PlotSummary;
import com.sni.bilanga.overview.dto.response.PlotTimeline;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OverviewService {

    /** État complet d'une parcelle. */
    PlotOverview forPlot(Long plotId);

    /**
     * Vue d'ensemble de l'exploitation, une ligne par parcelle.
     *
     * <p>Paginée : chaque ligne coûte plusieurs requêtes (dernier relevé,
     * alertes ouvertes, culture en cours, risques, dernier diagnostic, boîtiers).
     * Sans borne, le coût croissait linéairement avec le nombre de parcelles.
     * La pagination borne ce coût ; supprimer les requêtes par ligne relève d'un
     * travail distinct sur les agrégats.
     */
    Page<PlotSummary> forAllPlots(Pageable pageable);

    /**
     * Synthèse de l'exploitation entière, en une requête agrégée.
     *
     * Complète {@code forAllPlots} d'un niveau : celui qu'on regarde pour savoir
     * où aller, sans parcourir la liste page par page.
     */
    FarmOverview forFarm(Long userId);

    /**
     * Chronologie unifiée d'une parcelle : relevés marquants, diagnostics,
     * alertes, observations et changements de stade, dans l'ordre.
     *
     * <p>C'est la vue qui raconte l'histoire de la parcelle, et elle était
     * impossible à composer sans quatre appels et un tri côté client.
     *
     * @param types natures retenues ; {@code null} ou vide pour toutes. Le
     *              filtre n'est pas un confort d'affichage : chaque nature coûte
     *              une requête.
     */
    PlotTimeline timelineForPlot(Long plotId, java.time.Instant from, java.time.Instant to,
                                 java.util.Set<com.sni.bilanga.enums.TimelineEventType> types,
                                 Pageable pageable);
}
