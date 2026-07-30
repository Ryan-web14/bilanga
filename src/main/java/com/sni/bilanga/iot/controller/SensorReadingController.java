package com.sni.bilanga.iot.controller;


import com.sni.bilanga.enums.ReadingQuality;
import com.sni.bilanga.iot.dto.request.SensorReadingRequest;
import com.sni.bilanga.iot.dto.response.SensorReadingResponse;
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

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/readings")
public class SensorReadingController {

    private final SensorReadingService sensorReadingService;

    @PostMapping
    public ResponseEntity<ApiResponse<SensorReadingResponse>> create(
            @Valid @RequestBody SensorReadingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Relevé enregistré.", sensorReadingService.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SensorReadingResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(sensorReadingService.findById(id)));
    }

    /**
     * Série temporelle paginée et bornable dans le temps.
     *
     * {@code anomalyOnly=true} isole les relevés marqués comme physiquement
     * impossibles : c'est la vue à consulter pour repérer une sonde défaillante.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<SensorReadingResponse>>> search(
            @RequestParam(required = false) Long plotId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean anomalyOnly,
            @RequestParam(required = false) ReadingQuality quality,
            @PageableDefault(size = 50, sort = "recordedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                sensorReadingService.search(plotId, deviceId, from, to, anomalyOnly, quality, pageable))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        sensorReadingService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Relevé supprimé.", null));
    }
}
