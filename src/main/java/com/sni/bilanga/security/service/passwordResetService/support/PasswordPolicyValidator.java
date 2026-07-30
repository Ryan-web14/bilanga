package com.sni.bilanga.security.service.passwordResetService.support;

import com.sni.bilanga.exception.customs.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Central password strength policy, shared by every path that sets a new password
 * (server-rendered reset form and the JSON reset-confirm API).
 *
 * <p>The rules stay deliberately simple and length-first: a reasonable minimum length,
 * a mix of letters and digits, and a guard against the most obvious/common values. This
 * blocks the weakest passwords without frustrating users with rigid composition rules.
 */
@Component
public class PasswordPolicyValidator {

    public static final int MIN_LENGTH = 8;

    // Lowercased, exact-match blocklist of the most common weak passwords.
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "motdepasse", "12345678", "123456789", "1234567890",
            "azertyui", "azertyuiop", "qwertyui", "qwerty123", "00000000",
            "11111111", "abcd1234", "password1", "iloveyou", "admin123"
    );

    /**
     * @return the first violation message, or empty if the password satisfies the policy.
     */
    public Optional<String> validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Optional.of("Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères.");
        }
        if (password.chars().noneMatch(Character::isLetter)) {
            return Optional.of("Le mot de passe doit contenir au moins une lettre.");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            return Optional.of("Le mot de passe doit contenir au moins un chiffre.");
        }
        if (password.chars().distinct().count() <= 2) {
            return Optional.of("Le mot de passe est trop simple. Variez les caractères.");
        }
        if (COMMON_PASSWORDS.contains(password.toLowerCase())) {
            return Optional.of("Ce mot de passe est trop courant. Choisissez-en un autre.");
        }
        return Optional.empty();
    }

    /**
     * Enforces the policy, throwing {@link BadRequestException} on the first violation.
     * Used as the authoritative guard on the service layer.
     */
    public void validateOrThrow(String password) {
        validate(password).ifPresent(message -> {
            throw new BadRequestException(message);
        });
    }
}
