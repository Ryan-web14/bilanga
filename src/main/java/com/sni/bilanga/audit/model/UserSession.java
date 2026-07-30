package com.sni.bilanga.audit.model;


import com.sni.bilanga.annotation.IdGeneration;
import com.sni.bilanga.security.admin.user.model.Users;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @IdGeneration
    @Column(name = "id")
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users users;

    @Column(name = "refresh_token")
    private String refreshTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expired_at", nullable = false)
    private Instant expiredAt;

    @Column(name = "last_seen", nullable = false)
    private Instant lastAccessAt;

    @Column(name = "expires_in", nullable = false)
    private Long expiresIn;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "role_snapshot")
    private String roleSnapshot;

    @Column(name = "location_guess")
    private String locationGuess;

}
