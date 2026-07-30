package com.sni.bilanga.audit.service.implementation;


import com.sni.bilanga.audit.model.SettingsAuditLogs;
import com.sni.bilanga.audit.repository.SettingsAuditLogsRepository;
import com.sni.bilanga.audit.service.interfaces.SettingsAuditLogsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsAuditLogsServiceImpl implements SettingsAuditLogsService {

    private final SettingsAuditLogsRepository repository;

    @Override
    public Page<SettingsAuditLogs> list(String settingKey,
                                        Long changedBy,
                                        Instant changedFrom,
                                        Instant changedTo,
                                        Pageable pageable) {
        Specification<SettingsAuditLogs> specification = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(settingKey)) {
            String pattern = "%" + settingKey.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("settingKey")), pattern));
        }

        if (changedBy != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("changedBy"), changedBy));
        }

        if (changedFrom != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("changedAt"), changedFrom));
        }

        if (changedTo != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("changedAt"), changedTo));
        }

        return repository.findAll(specification, pageable);
    }
}
