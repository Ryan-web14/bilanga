package com.sni.bilanga.idempotency.service.implementation;

import com.sni.bilanga.idempotency.enums.IdempotencyStatus;
import com.sni.bilanga.idempotency.model.IdempotencyRecord;
import com.sni.bilanga.idempotency.repository.IdempotencyRecordRepository;
import com.sni.bilanga.idempotency.service.interfaces.IdempotencyAdminService;
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
public class IdempotencyAdminServiceImpl implements IdempotencyAdminService {

    private final IdempotencyRecordRepository repository;

    @Override
    public Page<IdempotencyRecord> list(String operation,
                                        String idempotencyKey,
                                        IdempotencyStatus status,
                                        Instant createdFrom,
                                        Instant createdTo,
                                        Pageable pageable) {
        Specification<IdempotencyRecord> specification = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(operation)) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(cb.upper(root.get("operation")), operation.trim().toUpperCase()));
        }

        if (StringUtils.hasText(idempotencyKey)) {
            String keyPattern = "%" + idempotencyKey.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("idempotencyKey")), keyPattern));
        }

        if (status != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("status"), status));
        }

        if (createdFrom != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
        }

        if (createdTo != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
        }

        return repository.findAll(specification, pageable);
    }
}
