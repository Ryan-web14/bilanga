package com.sni.bilanga.iot.controller;

import com.sni.bilanga.iot.dto.request.ObservationRequest;
import com.sni.bilanga.iot.dto.response.ObservationResponse;
import com.sni.bilanga.iot.service.interfaces.ObservationService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/observations")
public class ObservationController {

    private final ObservationService observationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ObservationResponse>> create(
            @Valid @RequestBody ObservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Observation enregistrée.", observationService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ObservationResponse>> update(@PathVariable Long id,
                                                                   @Valid @RequestBody ObservationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Observation mise à jour.",
                observationService.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ObservationResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(observationService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<ObservationResponse>>> search(
            @RequestParam(required = false) Long plotId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "observedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                observationService.search(plotId, userId, from, to, pageable))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        observationService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Observation supprimée.", null));
    }
}
