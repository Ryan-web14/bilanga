package com.sni.bilanga.farm.controller;


import com.sni.bilanga.farm.dto.response.CropJournalEntry;
import com.sni.bilanga.farm.dto.response.PlotSuccession;
import com.sni.bilanga.farm.service.interfaces.CropService;
import com.sni.bilanga.enums.HistoryGranularity;
import com.sni.bilanga.enums.PlotStatus;
import com.sni.bilanga.enums.SoilType;
import com.sni.bilanga.enums.TimelineEventType;
import com.sni.bilanga.harvest.dto.response.PlotEconomics;
import com.sni.bilanga.harvest.service.interfaces.HarvestService;
import com.sni.bilanga.overview.dto.response.PlotTimeline;
import com.sni.bilanga.overview.service.interfaces.OverviewService;
import com.sni.bilanga.utils.export.CsvSeriesWriter;
import com.sni.bilanga.farm.dto.request.PlotRequest;
import com.sni.bilanga.farm.dto.response.PlotResponse;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.iot.dto.response.PlotHistoryResponse;
import com.sni.bilanga.iot.service.interfaces.SensorReadingService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/plots")
public class PlotController {

    private final PlotService plotService;
    private final SensorReadingService sensorReadingService;
    private final OverviewService overviewService;
    private final HarvestService harvestService;
    private final CropService cropService;

    @PostMapping
    public ResponseEntity<ApiResponse<PlotResponse>> create(@Valid @RequestBody PlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Parcelle créée.", plotService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PlotResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody PlotRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Parcelle mise à jour.", plotService.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlotResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(plotService.findById(id)));
    }

    /**
     * Liste paginée et filtrable. Tous les critères sont facultatifs.
     * La réponse ramenait auparavant l'intégralité de la table.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<PlotResponse>>> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) PlotStatus status,
            @RequestParam(required = false) SoilType soilType,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                plotService.search(userId, status, soilType, q, pageable))));
    }

    /**
     * Série temporelle agrégée de la parcelle : min, moyenne et max par intervalle.
     *
     * Tracer une courbe sur un mois demandait jusqu'ici de rapatrier tous les
     * relevés bruts — plusieurs milliers pour une parcelle instrumentée — et de
     * les regrouper côté client. L'agrégation revient à la base.
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<PlotHistoryResponse>> history(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "DAY") HistoryGranularity granularity) {

        return ResponseEntity.ok(ApiResponse.success(
                sensorReadingService.history(id, from, to, granularity)));
    }

    /**
     * Chronologie unifiée : ce qui est arrivé à la parcelle, dans l'ordre.
     *
     * <p>Relevés marquants, diagnostics, alertes et leurs résolutions,
     * observations, changements de stade — en un seul flux trié. Composer cette
     * vue demandait jusqu'ici quatre appels et un tri côté client, refait
     * différemment par chacun.
     *
     * <p>{@code types} n'est pas un filtre d'affichage : chaque nature coûte une
     * requête, le transmettre évite d'interroger des sources dont on ne fera rien.
     */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<ApiResponse<PlotTimeline>> timeline(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Set<TimelineEventType> types,
            @PageableDefault(size = 50) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                overviewService.timelineForPlot(id, from, to, types, pageable)));
    }

    /**
     * L'histoire agronomique de la parcelle : les campagnes qui s'y sont succédé.
     *
     * <p><strong>Ce que cela met au jour.</strong> Une parcelle n'est pas une suite de
     * campagnes indépendantes : ce qui y a poussé l'an dernier conditionne ce qui y
     * pousse cette année. Le système stockait toutes les campagnes sans jamais les
     * présenter dans l'ordre — trois informations agronomiques réelles restaient donc
     * invisibles : les intervalles de sol nu, la monoculture, et le précédent cultural.
     *
     * <p>{@code monocultureWarnings} signale une même espèce cultivée deux campagnes de
     * suite ou plus : elle épuise les mêmes réserves du sol et concentre les ravageurs
     * qui lui sont propres. C'est le premier levier dont dispose un exploitant, et le
     * système avait l'information sans jamais la dire.
     *
     * <p>{@code endDateIsEstimated} distingue une fin <em>constatée</em> d'une fin
     * <em>prévue</em> : un intervalle calculé sur une date de récolte prévue n'a pas la
     * même valeur qu'un intervalle calculé sur un constat. À afficher.
     *
     * <p>⚠️ Les campagnes closes pour {@code ERREUR_DE_SAISIE} sont exclues : elles n'ont
     * jamais occupé le sol, et les compter fausserait le précédent cultural.
     */
    @GetMapping("/{id}/succession")
    public ResponseEntity<ApiResponse<PlotSuccession>> succession(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cropService.successionOf(id)));
    }

    /**
     * Journal des révisions de <strong>toutes</strong> les campagnes d'une parcelle.
     *
     * <p>Distinct de {@code GET /crops/{id}/journal}, qui ne couvre qu'une campagne :
     * celui-ci répond à « qu'a-t-on changé sur cette parcelle, depuis toujours ? ». C'est
     * cet axe de lecture qui justifie la dénormalisation de {@code plot_id} dans
     * {@code crop_journal}.
     *
     * <p>À ne pas confondre avec {@code /{id}/timeline} : la chronologie raconte ce qui
     * est <em>arrivé</em> à la parcelle (relevés, diagnostics, alertes), ce journal dit
     * ce que des <em>humains y ont changé</em>. Deux lectures distinctes.
     *
     * <p>Paginé, contrairement au journal d'une campagne : une parcelle cumule les
     * journaux de toutes ses campagnes successives, et le volume croît sans borne.
     */
    @GetMapping("/{id}/crop-journal")
    public ResponseEntity<ApiResponse<PaginatedResponse<CropJournalEntry>>> cropJournal(
            @PathVariable Long id,
            @PageableDefault(size = 30) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                cropService.journalForPlot(id, pageable))));
    }

    /**
     * Bilan économique : produit, charges, marge, rendement.
     *
     * <p>Tout est recalculé depuis les récoltes, les interventions et la surface
     * plantée — rien n'est ressaisi, ce qui est précisément ce qui fait la
     * valeur de l'agrégat : un total tenu à la main diverge dès la deuxième
     * campagne.
     *
     * <p>La réponse porte ses réserves : {@code missingData} liste ce qui
     * manque, {@code limitation} rappelle que la mise en regard « conseils
     * suivis / rendement » est un constat et non une démonstration.
     */
    @GetMapping("/{id}/economics")
    public ResponseEntity<ApiResponse<PlotEconomics>> economics(
            @PathVariable Long id,
            @RequestParam(required = false) Long cropId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(ApiResponse.success(
                harvestService.economics(id, cropId, from, to)));
    }

    /**
     * Série agrégée au format CSV.
     *
     * <p>Même agrégation que {@code /history}, sous une forme qu'un tableur
     * ouvre directement. Utile à l'exploitant, à l'agronome — et aux annexes du
     * mémoire, où une courbe se justifie par ses données.
     *
     * <p>Point-virgule et non virgule : c'est le séparateur qu'attend un tableur
     * configuré en français, où la virgule est le séparateur décimal.
     */
    @GetMapping(value = "/{id}/history.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> historyCsv(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "DAY") HistoryGranularity granularity) {

        PlotHistoryResponse history = sensorReadingService.history(id, from, to, granularity);

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"parcelle-" + id + "-historique.csv\"")
                .body(CsvSeriesWriter.write(history));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        plotService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Parcelle archivée.", null));
    }
}
