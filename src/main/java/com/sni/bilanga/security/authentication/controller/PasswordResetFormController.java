package com.sni.bilanga.security.authentication.controller;


import com.sni.bilanga.security.service.passwordResetService.interfaces.PasswordResetService;
import com.sni.bilanga.security.service.passwordResetService.support.PasswordPolicyValidator;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping(ApiPath.V1 + "/auth/password-reset")
public class PasswordResetFormController {

    private static final String CSRF_COOKIE = "RESET_FORM_CSRF";
    private static final String CSRF_FIELD = "_csrf";
    private static final String COOKIE_PATH = ApiPath.V1 + "/auth/password-reset";
    private static final Duration CSRF_TTL = Duration.ofMinutes(30);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetService passwordResetService;
    private final PasswordPolicyValidator passwordPolicyValidator;

    @GetMapping("/form")
    public String showForm(@RequestParam(required = false) String token,
                           Model model,
                           HttpServletRequest request,
                           HttpServletResponse response) {
        applySecurityHeaders(model, response);

        if (!StringUtils.hasText(token)) {
            model.addAttribute("reason", "Lien invalide. Aucun jeton fourni.");
            return "auth/password-reset-error";
        }
        if (!passwordResetService.isTokenValid(token)) {
            model.addAttribute("reason", "Ce lien de réinitialisation est invalide ou a expiré. Veuillez en demander un nouveau.");
            return "auth/password-reset-error";
        }

        model.addAttribute("token", token);
        model.addAttribute(CSRF_FIELD, issueCsrfToken(request, response));
        return "auth/password-reset-form";
    }

    @PostMapping("/form")
    public String processForm(@RequestParam String token,
                              @RequestParam String newPassword,
                              @RequestParam String confirmPassword,
                              @RequestParam(name = CSRF_FIELD, required = false) String csrfField,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              Model model) {
        applySecurityHeaders(model, response);

        // CSRF double-submit: the value in the hidden field must match the value in the cookie.
        if (!csrfMatches(request, csrfField)) {
            model.addAttribute("reason", "Votre session a expiré. Veuillez rouvrir le lien reçu par email et réessayer.");
            return "auth/password-reset-error";
        }

        if (!StringUtils.hasText(token)) {
            model.addAttribute("reason", "Jeton manquant. Veuillez utiliser le lien reçu par email.");
            return "auth/password-reset-error";
        }

        String policyViolation = passwordPolicyValidator.validate(newPassword).orElse(null);
        if (policyViolation != null) {
            return renderFormWithError(model, request, response, token, policyViolation);
        }

        if (!newPassword.equals(confirmPassword)) {
            return renderFormWithError(model, request, response, token, "Les mots de passe ne correspondent pas.");
        }

        try {
            passwordResetService.validatePasswordResetToken(token, newPassword);
            clearCsrfCookie(request, response);
            return "auth/password-reset-success";
        } catch (Exception ex) {
            log.warn("Password reset form submission failed: {}", ex.getMessage());
            model.addAttribute("reason", ex.getMessage());
            return "auth/password-reset-error";
        }
    }

    private String renderFormWithError(Model model, HttpServletRequest request, HttpServletResponse response,
                                       String token, String error) {
        model.addAttribute("token", token);
        model.addAttribute("error", error);
        model.addAttribute(CSRF_FIELD, issueCsrfToken(request, response));
        return "auth/password-reset-form";
    }

    // ── CSRF (double-submit cookie, works statelessly) ──────────────────────────

    private String issueCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        byte[] raw = new byte[32];
        SECURE_RANDOM.nextBytes(raw);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(CSRF_TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return value;
    }

    private boolean csrfMatches(HttpServletRequest request, String csrfField) {
        if (!StringUtils.hasText(csrfField) || request.getCookies() == null) {
            return false;
        }
        String cookieValue = null;
        for (Cookie cookie : request.getCookies()) {
            if (CSRF_COOKIE.equals(cookie.getName())) {
                cookieValue = cookie.getValue();
                break;
            }
        }
        if (!StringUtils.hasText(cookieValue)) {
            return false;
        }
        return MessageDigest.isEqual(
                cookieValue.getBytes(StandardCharsets.UTF_8),
                csrfField.getBytes(StandardCharsets.UTF_8));
    }

    private void clearCsrfCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE, "")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // ── Per-page security headers (strict CSP + no-referrer) ────────────────────

    private void applySecurityHeaders(Model model, HttpServletResponse response) {
        String nonce = newNonce();
        model.addAttribute("nonce", nonce);
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; "
                        + "form-action 'self'; img-src data:; style-src 'unsafe-inline'; "
                        + "script-src 'nonce-" + nonce + "'");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private String newNonce() {
        byte[] raw = new byte[16];
        SECURE_RANDOM.nextBytes(raw);
        return Base64.getEncoder().withoutPadding().encodeToString(raw);
    }
}
