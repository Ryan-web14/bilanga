package com.sni.bilanga.security.admin.user.dto.request;

import lombok.Data;

@Data
public class AdminResetUserPasswordRequest {
    private String newPassword;
    private Boolean generatePassword;
}
