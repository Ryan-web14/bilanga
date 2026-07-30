package com.sni.bilanga.audit.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class UserSessionResponse {
    private String sessionId;
    private String email;
    private Instant createdAt;
    private Instant lastAccessAt;
    private String expiresIn;
    private String ipAddress;
    private String userAgent;
    private String deviceType;
    private String status;
    private String role;
    private String locationGuess;
}
