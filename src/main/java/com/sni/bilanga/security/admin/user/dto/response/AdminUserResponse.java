package com.sni.bilanga.security.admin.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminUserResponse {
    private Long id;
    private String userId;
    private String email;
    private String firstname;
    private String lastname;

    /** Destinataire des alertes SMS ; nul, l'utilisateur reste injoignable au champ. */
    private String phone;
    private Boolean accountEnabled;
    private Boolean accountLocked;
    private Boolean accountExpired;
    private Boolean deleted;
    private Integer failedLoginAttempts;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> roleNames;
    private String generatedPassword;
}
