package com.sni.bilanga.security.service.passwordResetService.interfaces;


import com.sni.bilanga.security.admin.user.model.Users;

public interface PasswordResetService {

    void generatePasswordResetToken(Users user);
    void generatePasswordResetToken(Users user, String triggeredBy);
    void validatePasswordResetToken(String token, String newPassword);

    /** Vérifie si un token est valide (non expiré, non utilisé) sans le consommer. */
    boolean isTokenValid(String token);
}
