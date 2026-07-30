package com.sni.bilanga.idempotency.service.interfaces;


import com.sni.bilanga.idempotency.enums.IdempotencyStatus;
import com.sni.bilanga.idempotency.model.IdempotencyRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface IdempotencyAdminService {

    Page<IdempotencyRecord> list(String operation,
                                 String idempotencyKey,
                                 IdempotencyStatus status,
                                 Instant createdFrom,
                                 Instant createdTo,
                                 Pageable pageable);
}
