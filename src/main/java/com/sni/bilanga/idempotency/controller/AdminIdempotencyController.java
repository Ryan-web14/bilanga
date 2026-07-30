package com.sni.bilanga.idempotency.controller;


import com.sni.bilanga.idempotency.dto.response.IdempotencyRecordResponse;
import com.sni.bilanga.idempotency.enums.IdempotencyStatus;
import com.sni.bilanga.idempotency.model.IdempotencyRecord;
import com.sni.bilanga.idempotency.service.interfaces.IdempotencyAdminService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/admin/idempotency-records")
public class AdminIdempotencyController {

    private final IdempotencyAdminService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:AUDIT')")
    public ResponseEntity<ApiResponse<PaginatedResponse<IdempotencyRecordResponse>>> list(
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String idempotencyKey,
            @RequestParam(required = false) IdempotencyStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                service.list(operation, idempotencyKey, status, createdFrom, createdTo, pageable)
                        .map(this::toResponse))));
    }

    /**
     * Le corps de réponse mémorisé n'est pas repris : c'est la réponse complète
     * d'une opération d'administration, que la consultation du journal
     * divulguait jusqu'ici intégralement.
     */
    private IdempotencyRecordResponse toResponse(IdempotencyRecord record) {
        String body = record.getResponseBody();

        return IdempotencyRecordResponse.builder()
                .id(record.getId())
                .operation(record.getOperation())
                .idempotencyKey(record.getIdempotencyKey())
                .requestHash(record.getRequestHash())
                .status(record.getStatus())
                .hasStoredResponse(body != null && !body.isBlank())
                .storedResponseLength(body == null ? 0 : body.length())
                .errorBody(record.getErrorBody())
                .completedAt(record.getCompletedAt())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
