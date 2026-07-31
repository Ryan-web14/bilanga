package com.sni.bilanga.mailService.model;

import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.mailService.enums.EmailDeliveryStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "email_delivery_log", indexes = {
        @Index(name = "idx_email_delivery_number", columnList = "email_number"),
        @Index(name = "idx_email_delivery_status", columnList = "status,created_at"),
        @Index(name = "idx_email_delivery_recipient", columnList = "recipient_email"),
        @Index(name = "idx_email_delivery_related", columnList = "related_type,related_code")
})
public class EmailDeliveryLog {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Column(name = "email_number", nullable = false, unique = true, length = 100)
    private String emailNumber;

    @Column(name = "provider", nullable = false, length = 60)
    private String provider;

    @Column(name = "from_email")
    private String fromEmail;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body_type", nullable = false, length = 20)
    private String bodyType;

    @Column(name = "body_content", columnDefinition = "text")
    private String bodyContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private EmailDeliveryStatus status = EmailDeliveryStatus.QUEUED;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "related_type", length = 80)
    private String relatedType;

    @Column(name = "related_code", length = 120)
    private String relatedCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = EmailDeliveryStatus.QUEUED;
        }
        if (attempts == null) {
            attempts = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
