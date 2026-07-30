package com.sni.bilanga.audit.dto.response;

import com.sni.bilanga.audit.enums.AuditStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Vue de lecture d'une entrée du journal d'audit.
 *
 * L'entité était renvoyée telle quelle, ce qui liait le contrat d'API au schéma
 * et exposait les colonnes {@code jsonb} sans contrôle.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class AuditLogResponse {

    private Long id;
    private Instant createdAt;

    private Long actorId;
    private String actorEmail;

    private String module;
    private String action;
    private String ressource;

    private AuditStatus auditStatus;

    private String ipAddress;
    private String userAgent;
    private String sessionId;

    private String errorCode;
    private String errorMessage;

    private Map<String, Object> metadata;
    private Map<String, Object> diff;
}
