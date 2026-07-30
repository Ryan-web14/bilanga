package com.sni.bilanga.diagnosis.controller;

import com.sni.bilanga.diagnosis.dto.request.RecommendationFeedbackRequest;
import com.sni.bilanga.diagnosis.dto.response.RecommendationResponse;
import com.sni.bilanga.diagnosis.dto.response.RecommendationUptake;
import com.sni.bilanga.diagnosis.service.interfaces.RecommendationService;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.enums.RecommendationStatus;
import com.sni.bilanga.enums.RecommendationType;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Suivi des conseils émis.
 *
 * Les recommandations sont produites par le moteur de diagnostic — ce
 * contrôleur ne permet donc ni d'en créer ni d'en supprimer, seulement de les
 * consulter et de leur donner suite.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RecommendationResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(recommendationService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<RecommendationResponse>>> search(
            @RequestParam(required = false) Long plotId,
            @RequestParam(required = false) Long diagnosticId,
            @RequestParam(required = false) RecommendationStatus status,
            @RequestParam(required = false) RecommendationPriority priority,
            @RequestParam(required = false) RecommendationType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                recommendationService.search(plotId, diagnosticId, status, priority, type,
                        from, to, pageable))));
    }

    /**
     * Enregistre la suite donnée : {@code APPLIQUEE} ou {@code IGNOREE}.
     *
     * C'est la boucle de retour qui manquait : sans elle, tous les conseils
     * restaient indéfiniment « à traiter » et rien ne permettait de mesurer si
     * le moteur conseille juste.
     */
    @PatchMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<RecommendationResponse>> recordFeedback(
            @PathVariable Long id, @Valid @RequestBody RecommendationFeedbackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Retour enregistré.",
                recommendationService.recordFeedback(id, request)));
    }

    /** Taux d'application par moteur d'origine. */
    @GetMapping("/uptake")
    public ResponseEntity<ApiResponse<List<RecommendationUptake>>> uptake(
            @RequestParam(required = false) Long plotId) {
        return ResponseEntity.ok(ApiResponse.success(recommendationService.uptake(plotId)));
    }
}
