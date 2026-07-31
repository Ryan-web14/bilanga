package com.sni.bilanga.mailService.implementation;

import com.sni.bilanga.mailService.baseService.DefaultEmailSender;
import com.sni.bilanga.mailService.enums.EmailPriority;
import com.sni.bilanga.mailService.interfaces.OttMailService;
import com.sni.bilanga.security.admin.user.model.Users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
@Slf4j
public class OttMailServiceImpl implements OttMailService {

    private final DefaultEmailSender emailSender;
    private final SpringTemplateEngine emailTemplteEngine;

    @Override
    @Async
    public CompletableFuture<Boolean> sendOneTimeTokenMail(Users user, String token) {
        String email = user.getEmail();
        String firstname = StringUtils.hasText(user.getFirstname())
                ? user.getFirstname()
                : (email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : email);

        Context context = new Context();
        context.setVariable("firstname", firstname);
        context.setVariable("token", token);
        List<String> digits = new java.util.ArrayList<>();
        for (char c : token.toCharArray()) {
            digits.add(String.valueOf(c));
        }
        context.setVariable("tokenDigits", digits);

        String template = emailTemplteEngine.process("ott-login", context);

        try {
            String subject = "Votre code de connexion";
            log.info("OTP queued to {} [CRITICAL]", user.getEmail());
            return emailSender.sendHtmlEmail(user.getEmail(), subject, template, EmailPriority.CRITICAL);
        } catch (RuntimeException e) {
            log.error("Could not queue OTP for {}", user.getEmail());
            return CompletableFuture.completedFuture(false);
        }
    }
}