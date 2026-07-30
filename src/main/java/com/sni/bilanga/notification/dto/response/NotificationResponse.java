package com.sni.bilanga.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class NotificationResponse {

    private Long id;
    private Long alertId;
    private Long plotId;

    private String channel;
    private String recipient;
    private String subject;
    private String body;

    private String level;

    private String status;
    private String statusLabel;

    private Integer attempts;
    private String lastError;

    private Instant createdAt;
    private Instant lastAttemptAt;
    private Instant sentAt;
}
