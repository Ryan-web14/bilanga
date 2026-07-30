package com.sni.bilanga.audit.service.interfaces;

import com.sni.bilanga.audit.model.SettingsAuditLogs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface SettingsAuditLogsService {

    Page<SettingsAuditLogs> list(String settingKey,
                                 Long changedBy,
                                 Instant changedFrom,
                                 Instant changedTo,
                                 Pageable pageable);
}
