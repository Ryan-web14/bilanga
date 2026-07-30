package com.sni.bilanga.diagnosis.controller;

import com.sni.bilanga.diagnosis.dto.request.AlertAssignmentRequest;
import com.sni.bilanga.diagnosis.dto.response.AlertResponse;
import com.sni.bilanga.diagnosis.service.interfaces.AlertService;
import com.sni.bilanga.enums.AlertCategory;
import com.sni.bilanga.enums.AlertLevel;
import com.sni.bilanga.enums.AlertStatus;
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

/**
 * Les alertes ne sont pas créées par un client : elles sont levées par le
 * moteur de diagnostic (voir {@link AlertService#raiseIfNeeded}). Ce contrôleur
 * n'expose donc que la consultation et le cycle de vie (acquittement, résolution).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/alerts")
public class AlertController {

    private final AlertService alertService;

    /**
     * Liste paginée et filtrable. {@code openOnly=true} donne la vue tableau de
     * bord ; les bornes temporelles servent au suivi rétrospectif, qui n'était
     * pas consultable jusqu'ici.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<AlertResponse>>> search(
            @RequestParam(required = false) Long plotId,
            @RequestParam(required = false) AlertCategory category,
            @RequestParam(required = false) AlertLevel level,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                alertService.search(plotId, category, level, status, openOnly, from, to, pageable))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AlertResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(alertService.findById(id)));
    }

    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<ApiResponse<AlertResponse>> acknowledge(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Alerte acquittée.", alertService.acknowledge(id)));
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<AlertResponse>> resolve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Alerte résolue.", alertService.resolve(id)));
    }

    /**
     * Désigne un responsable et, éventuellement, un terme.
     *
     * Une alerte que personne ne s'est vu confier reste dans la liste de tout le
     * monde — donc dans celle de personne.
     */
    @PatchMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<AlertResponse>> assign(
            @PathVariable Long id,
            @Valid @RequestBody AlertAssignmentRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Alerte affectée.",
                alertService.assign(id, request.getUserId(), request.getDueAt())));
    }
}
