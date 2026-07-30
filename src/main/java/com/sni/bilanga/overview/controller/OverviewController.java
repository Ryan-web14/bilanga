package com.sni.bilanga.overview.controller;


import com.sni.bilanga.harvest.dto.response.PlotEconomics;
import com.sni.bilanga.harvest.service.interfaces.HarvestService;
import com.sni.bilanga.overview.dto.response.FarmOverview;
import com.sni.bilanga.overview.dto.response.PlotOverview;
import com.sni.bilanga.overview.dto.response.PlotSummary;
import com.sni.bilanga.overview.service.interfaces.OverviewService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/overview")
public class OverviewController {

    private final OverviewService overviewService;
    private final HarvestService harvestService;

    /** Vue d'ensemble de l'exploitation, une ligne par parcelle. */
    @GetMapping("/plots")
    public ResponseEntity<ApiResponse<PaginatedResponse<PlotSummary>>> all(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                overviewService.forAllPlots(pageable))));
    }

    /** État complet d'une parcelle. */
    @GetMapping("/plots/{plotId}")
    public ResponseEntity<ApiResponse<PlotOverview>> one(@PathVariable Long plotId) {
        return ResponseEntity.ok(ApiResponse.success(overviewService.forPlot(plotId)));
    }

    /**
     * Synthèse de l'exploitation entière.
     *
     * Une seule requête agrégée, quel que soit le nombre de parcelles — là où la
     * vue par parcelle en coûte plusieurs par ligne. C'est l'écran d'accueil :
     * combien de parcelles, dans quel état, et lesquelles regarder d'abord.
     */
    @GetMapping("/farm")
    public ResponseEntity<ApiResponse<FarmOverview>> farm(
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(ApiResponse.success(overviewService.forFarm(userId)));
    }

    /**
     * Bilan économique de chaque parcelle, pour les comparer.
     *
     * <p>C'est la comparaison qui informe, pas le chiffre isolé : savoir qu'une
     * parcelle dégage 180 000 F de marge ne dit rien tant qu'on ignore ce que
     * font les autres. Le classement porte sur la marge à l'hectare, seul
     * critère comparable entre des surfaces différentes.
     *
     * <p>Chaque ligne porte ses propres réserves : données manquantes, et rappel
     * que le rapprochement « conseils suivis / rendement » est descriptif.
     */
    @GetMapping("/economics")
    public ResponseEntity<ApiResponse<List<PlotEconomics>>> economics(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(ApiResponse.success(
                harvestService.economicsForAllPlots(userId, from, to)));
    }
}
