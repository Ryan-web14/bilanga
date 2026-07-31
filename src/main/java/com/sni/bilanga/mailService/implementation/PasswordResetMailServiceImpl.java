package com.sni.bilanga.mailService.implementation;

import com.sni.bilanga.mailService.baseService.DefaultEmailSender;
import com.sni.bilanga.mailService.enums.EmailPriority;
import com.sni.bilanga.mailService.interfaces.PasswordResetMailService;
import com.sni.bilanga.security.admin.user.model.Users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
@Slf4j
public class PasswordResetMailServiceImpl implements PasswordResetMailService {

    private static final String FORM_PATH = "/sni/api/v1/auth/password-reset/form?token=";

    private final DefaultEmailSender emailSender;
    private final SpringTemplateEngine emailTemplteEngine;

    @Value("${app.api-base-url:}")
    private String apiBaseUrl;

    @Async
    @Override
    public CompletableFuture<Boolean> sendPasswordResetMail(Users user, String rawToken, long expirationMs) {
        String firstname = user.getEmail();
        String link = apiBaseUrl + FORM_PATH + rawToken;

        long totalMinutes = expirationMs / (1000 * 60);
        String expiryLabel = totalMinutes >= 60
                ? (totalMinutes / 60) + " heure" + (totalMinutes / 60 > 1 ? "s" : "")
                : totalMinutes + " min";

        Context context = new Context();
        context.setVariable("firstname", firstname);
        context.setVariable("resetLink", link);
        context.setVariable("expiryMinute", expiryLabel);

        String template = emailTemplteEngine.process("password_reset", context);

        try {
            String subject = "Réinitialisation de mot de passe";
            log.info("Password reset email queued [CRITICAL]");
            return emailSender.sendHtmlEmail(user.getEmail(), subject, template, EmailPriority.CRITICAL);
        } catch (RuntimeException ex) {
            log.error("Could not queue password reset mail to {}", user.getEmail());
            return CompletableFuture.completedFuture(false);
        }
    }
}