package com.sni.bilanga.notification.controller;

import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.NotificationStatus;
import com.sni.bilanga.notification.dto.response.NotificationResponse;
import com.sni.bilanga.notification.model.NotificationOutbox;
import com.sni.bilanga.notification.repository.NotificationOutboxRepository;
import com.sni.bilanga.notification.service.NotificationService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

/**
 * Suivi des envois.
 *
 * Faute d'ordonnanceur, la reprise des notifications en échec est déclenchée
 * soit par un nouvel événement, soit ici, à la main. C'est le filet de sécurité
 * assumé du dispositif : on préfère une reprise explicite à un envoi perdu.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/admin/notifications")
public class AdminNotificationController {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationService notificationService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PaginatedResponse<NotificationResponse>>> list(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long plotId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                outboxRepository.search(DomainEnums.nameOf(status),
                                channel == null || channel.isBlank()
                                        ? null : channel.trim().toUpperCase(Locale.ROOT),
                                plotId, pageable)
                        .map(this::toResponse))));
    }

    /** Relance les envois encore en attente ou en échec. */
    @PostMapping("/dispatch")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> dispatch(
            @RequestParam(defaultValue = "50") int batchSize) {

        int sent = notificationService.dispatchPending(batchSize);
        return ResponseEntity.ok(ApiResponse.success(
                "Reprise effectuée.", Map.of("sent", sent)));
    }

    private NotificationResponse toResponse(NotificationOutbox n) {
        NotificationStatus status = NotificationStatus.from(n.getStatus());

        return NotificationResponse.builder()
                .id(n.getId())
                .alertId(n.getAlertId())
                .plotId(n.getPlotId())
                .channel(n.getChannel())
                .recipient(n.getRecipient())
                .subject(n.getSubject())
                .body(n.getBody())
                .level(n.getLevel())
                .status(n.getStatus())
                .statusLabel(status == null ? null : status.getLabel())
                .attempts(n.getAttempts())
                .lastError(n.getLastError())
                .createdAt(n.getCreatedAt())
                .lastAttemptAt(n.getLastAttemptAt())
                .sentAt(n.getSentAt())
                .build();
    }
}
