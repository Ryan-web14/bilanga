package com.sni.bilanga.audit.controller;


import com.sni.bilanga.audit.dto.response.SettingsAuditLogResponse;
import com.sni.bilanga.audit.model.SettingsAuditLogs;
import com.sni.bilanga.audit.service.interfaces.SettingsAuditLogsService;
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
@RequestMapping(ApiPath.V1 + "/admin/settings-audit-logs")
public class AdminSettingsAuditController {

    private final SettingsAuditLogsService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:AUDIT')")
    public ResponseEntity<ApiResponse<PaginatedResponse<SettingsAuditLogResponse>>> list(
            @RequestParam(required = false) String settingKey,
            @RequestParam(required = false) Long changedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant changedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant changedTo,
            @PageableDefault(size = 20, sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                service.list(settingKey, changedBy, changedFrom, changedTo, pageable)
                        .map(this::toResponse))));
    }

    private SettingsAuditLogResponse toResponse(SettingsAuditLogs log) {
        return SettingsAuditLogResponse.builder()
                .id(log.getId())
                .settingKey(log.getSettingKey())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .changedBy(log.getChangedBy())
                .changedAt(log.getChangedAt())
                .reason(log.getReason())
                .build();
    }
}
