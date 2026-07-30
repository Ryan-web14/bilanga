package com.sni.bilanga.audit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Vue de lecture d'un changement de configuration. */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SettingsAuditLogResponse {

    private Long id;
    private String settingKey;
    private String oldValue;
    private String newValue;
    private Long changedBy;
    private Instant changedAt;
    private String reason;
}
