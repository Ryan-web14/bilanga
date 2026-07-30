package com.sni.bilanga.security.authentication.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PasswordResetConfirmationRequest {

    @NotBlank
    private String token;

    @NotBlank
    private String newPassword;
}
