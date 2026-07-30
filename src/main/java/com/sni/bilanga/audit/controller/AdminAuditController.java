package com.sni.bilanga.audit.controller;


import com.sni.bilanga.audit.dto.response.AuditLogResponse;
import com.sni.bilanga.audit.enums.AuditStatus;
import com.sni.bilanga.audit.model.AuditLog;
import com.sni.bilanga.audit.service.interfaces.AuditService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/admin/audit-logs")
public class AdminAuditController {

    private final AuditService auditService;

    /**
     * Le journal d'audit nomme les acteurs, leurs adresses IP et leurs actions :
     * sa consultation appelait une permission explicite, qu'aucune annotation
     * ne portait.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:AUDIT')")
    public ResponseEntity<ApiResponse<PaginatedResponse<AuditLogResponse>>> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) AuditStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                auditService.list(module, action, actorEmail, status, createdFrom, createdTo, pageable)
                        .map(this::toResponse))));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .createdAt(log.getCreatedAt())
                .actorId(log.getActorId())
                .actorEmail(log.getActorEmail())
                .module(log.getModule())
                .action(log.getAction())
                .ressource(log.getRessource())
                .auditStatus(log.getAuditStatus())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .sessionId(log.getSessionId())
                .errorCode(log.getErrorCode())
                .errorMessage(log.getErrorMessage())
                .metadata(log.getMetadata())
                .diff(log.getDiff())
                .build();
    }
}
