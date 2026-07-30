package com.sni.bilanga.audit.model;


import com.sni.bilanga.annotation.IdGeneration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "setting_audit_logs")
public class SettingsAuditLogs {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Column(name = "setting_key")
    private String settingKey;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at")
    private Instant changedAt;

    @Column(name = "reason")
    private String reason;
}
