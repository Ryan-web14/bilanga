package com.sni.bilanga.security.service.passwordResetService.support;

import org.springframework.stereotype.Component;

@Component
public class PasswordResetNotifier {

    public void sendResetLink(String email, String token) {
        System.out.println("[DEV] Lien de réinitialisation pour " + email + " : token=" + token);
    }
}