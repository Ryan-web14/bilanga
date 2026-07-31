package com.sni.bilanga.mailService.dto.response;

import com.sni.bilanga.mailService.enums.EmailDeliveryStatus;

import java.time.Instant;

public record EmailDeliveryResponse(
        String emailNumber,
        String provider,
        String fromEmail,
        String recipientEmail,
        String subject,
        String bodyType,
        EmailDeliveryStatus status,
        Integer attempts,
        String lastError,
        String relatedType,
        String relatedCode,
        Instant createdAt,
        Instant updatedAt,
        Instant sentAt,
        Instant failedAt
) {
}
