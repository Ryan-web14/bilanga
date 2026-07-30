package com.sni.bilanga.audit.repository;

import com.sni.bilanga.audit.model.SettingsAuditLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SettingsAuditLogsRepository extends JpaRepository<SettingsAuditLogs, Long>, JpaSpecificationExecutor<SettingsAuditLogs> {
}
