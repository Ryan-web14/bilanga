package com.sni.bilanga.idempotency.repository;

import com.sni.bilanga.idempotency.model.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long>, JpaSpecificationExecutor<IdempotencyRecord> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(String operation, String idempotencyKey);
}
