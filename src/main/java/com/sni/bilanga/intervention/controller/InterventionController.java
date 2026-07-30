package com.sni.bilanga.intervention.controller;

import com.sni.bilanga.enums.InterventionType;
import com.sni.bilanga.intervention.dto.request.InterventionRequest;
import com.sni.bilanga.intervention.dto.response.InterventionEffect;
import com.sni.bilanga.intervention.dto.response.InterventionResponse;
import com.sni.bilanga.intervention.service.interfaces.InterventionService;
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

/**
 * Journal des actions menées au champ.
 *
 * <p>Contrairement aux alertes et aux recommandations, qui sont produites par le
 * moteur, les interventions sont <em>déclarées</em> : elles constatent ce que
 * l'exploitant a fait. C'est ce qui ferme la boucle conseil → action → effet.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/interventions")
public class InterventionController {

    private final InterventionService interventionService;

    @PostMapping
    public ResponseEntity<ApiResponse<InterventionResponse>> create(
            @Valid @RequestBody InterventionRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Intervention enregistrée.",
                        interventionService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InterventionResponse>> update(
            @PathVariable Long id, @Valid @RequestBody InterventionRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Intervention mise à jour.",
                interventionService.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InterventionResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(interventionService.findById(id)));
    }

    /** Liste paginée et filtrable ; tous les critères sont facultatifs. */
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<InterventionResponse>>> search(
            @RequestParam(required = false) Long plotId,
            @RequestParam(required = false) Long cropId,
            @RequestParam(required = false) InterventionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "performedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                interventionService.search(plotId, cropId, type, from, to, pageable))));
    }

    /**
     * Ce que l'intervention a changé.
     *
     * <p>Compare les mesures des quarante-huit heures qui précèdent à celles des
     * quarante-huit heures qui suivent. Le verdict constate une évolution ; il
     * n'établit pas une causalité, et la réponse porte cette réserve — une
     * pluie survenue dans la même fenêtre produirait le même chiffre.
     */
    @GetMapping("/{id}/effect")
    public ResponseEntity<ApiResponse<InterventionEffect>> effect(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(interventionService.effect(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        interventionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Intervention supprimée.", null));
    }
}
