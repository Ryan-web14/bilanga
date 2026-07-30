package com.sni.bilanga.idempotency.service.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sni.bilanga.exception.customs.BadRequestException;
import com.sni.bilanga.exception.customs.ConflictException;
import com.sni.bilanga.idempotency.enums.IdempotencyStatus;
import com.sni.bilanga.idempotency.model.IdempotencyRecord;
import com.sni.bilanga.idempotency.repository.IdempotencyRecordRepository;
import com.sni.bilanga.idempotency.service.interfaces.IdempotencyService;
import com.sni.bilanga.idempotency.service.model.IdempotencyExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Override
    public <T> IdempotencyExecutionResult<T> execute(String operation,
                                                     String idempotencyKey,
                                                     Object requestPayload,
                                                     Class<T> responseType,
                                                     Supplier<T> callback) {
        if (!StringUtils.hasText(operation)) {
            throw new BadRequestException("Idempotency operation is required");
        }

        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BadRequestException("Idempotency key is required");
        }

        String normalizedOperation = operation.trim().toUpperCase();
        String normalizedKey = idempotencyKey.trim();
        String requestHash = hash(requestPayload);

        IdempotencyRecord claimed = inNewTransaction(() -> claim(normalizedOperation, normalizedKey, requestHash));

        if (claimed.getStatus() == IdempotencyStatus.COMPLETED) {
            return IdempotencyExecutionResult.<T>builder()
                    .replayed(true)
                    .payload(deserialize(claimed.getResponseBody(), responseType))
                    .build();
        }

        try {
            T result = callback.get();
            String responseBody = serialize(result);
            inNewTransaction(() -> complete(claimed.getId(), responseBody));
            return IdempotencyExecutionResult.<T>builder()
                    .replayed(false)
                    .payload(result)
                    .build();
        } catch (RuntimeException ex) {
            inNewTransaction(() -> fail(claimed.getId(), ex.getMessage()));
            throw ex;
        }
    }

    private IdempotencyRecord claim(String operation, String key, String requestHash) {
        IdempotencyRecord existing = repository.findByOperationAndIdempotencyKey(operation, key).orElse(null);

        if (existing == null) {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .operation(operation)
                    .idempotencyKey(key)
                    .requestHash(requestHash)
                    .status(IdempotencyStatus.PROCESSING)
                    .build();
            return repository.save(record);
        }

        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException("idempotency request", "the same key was reused with a different payload");
        }

        if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
            throw new ConflictException("idempotency request", "another request with the same key is still processing");
        }

        if (existing.getStatus() == IdempotencyStatus.FAILED) {
            existing.setStatus(IdempotencyStatus.PROCESSING);
            existing.setErrorBody(null);
            existing.setResponseBody(null);
            existing.setCompletedAt(null);
            return repository.save(existing);
        }

        return existing;
    }

    private Void complete(Long id, String responseBody) {
        IdempotencyRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Idempotency record not found"));
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setResponseBody(responseBody);
        record.setCompletedAt(java.time.Instant.now());
        repository.save(record);
        return null;
    }

    private Void fail(Long id, String errorBody) {
        IdempotencyRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Idempotency record not found"));
        record.setStatus(IdempotencyStatus.FAILED);
        record.setErrorBody(errorBody);
        repository.save(record);
        return null;
    }

    private String serialize(Object payload) {
        if (payload == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize idempotency payload", ex);
        }
    }

    private <T> T deserialize(String payload, Class<T> responseType) {
        if (payload == null || responseType == null || responseType == Void.class) {
            return null;
        }

        try {
            return objectMapper.readValue(payload, responseType);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not deserialize idempotency payload", ex);
        }
    }

    private String hash(Object requestPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String serializedPayload = serialize(requestPayload);
            byte[] hash = digest.digest(String.valueOf(serializedPayload).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private <T> T inNewTransaction(TransactionSupplier<T> supplier) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> supplier.get());
    }

    @FunctionalInterface
    private interface TransactionSupplier<T> {
        T get();
    }
}
