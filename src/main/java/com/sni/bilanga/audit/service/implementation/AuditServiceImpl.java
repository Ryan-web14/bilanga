package com.sni.bilanga.audit.service.implementation;



import com.sni.bilanga.audit.enums.AuditStatus;
import com.sni.bilanga.audit.model.AuditLog;
import com.sni.bilanga.audit.repository.AuditLogRepository;
import com.sni.bilanga.audit.service.interfaces.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@RequiredArgsConstructor
@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditRepo;

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditLog auditLog) {
        auditRepo.save(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLog> list(String module,
                               String action,
                               String actorEmail,
                               AuditStatus status,
                               Instant createdFrom,
                               Instant createdTo,
                               Pageable pageable) {
        Specification<AuditLog> specification = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(module)) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(cb.upper(root.get("module")), module.trim().toUpperCase()));
        }

        if (StringUtils.hasText(action)) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(cb.upper(root.get("action")), action.trim().toUpperCase()));
        }

        if (StringUtils.hasText(actorEmail)) {
            String emailPattern = "%" + actorEmail.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("actorEmail")), emailPattern));
        }

        if (status != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("auditStatus"), status));
        }

        if (createdFrom != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
        }

        if (createdTo != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
        }

        return auditRepo.findAll(specification, pageable);
    }
}
