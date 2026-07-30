package com.sni.bilanga.idempotency.service.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IdempotencyExecutionResult<T> {

    private final boolean replayed;
    private final T payload;
}
