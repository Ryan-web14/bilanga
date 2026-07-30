package com.sni.bilanga.audit.model;

import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.audit.enums.AuditStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_email", nullable = false)
    private String actorEmail;

    @Column(name = "action")
    private String action;

    @Column(name = "ressource")
    private String ressource;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_status")
    private AuditStatus auditStatus;

    @Column(name = "module", nullable = false)
    private String module;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff_json", columnDefinition = "jsonb")
    private Map<String, Object> diff;


}
