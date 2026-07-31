package com.sni.bilanga.security.service.passwordResetService.implementation;

import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.exception.customs.BadRequestException;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.admin.user.service.interfaces.UserService;
import com.sni.bilanga.security.model.PasswordResetToken;
import com.sni.bilanga.security.repository.PasswordResetTokenRepository;
import com.sni.bilanga.security.repository.RefreshTokenRepository;
import com.sni.bilanga.security.service.passwordResetService.interfaces.PasswordResetService;
import com.sni.bilanga.security.service.passwordResetService.support.PasswordPolicyValidator;
import com.sni.bilanga.security.service.passwordResetService.support.PasswordResetNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final PasswordResetTokenRepository passwordResetTokenRepo;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetNotifier notifier;
    private final com.sni.bilanga.mailService.interfaces.PasswordResetMailService passwordResetMailService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AppProperties.Security securityConfig;

    /** Durée de vie d'un jeton de réinitialisation. */
    private long resetExpirationMs() {
        return securityConfig.getPasswordReset().getExpirationMs();
    }


    @Override
    @Transactional
    public void generatePasswordResetToken(Users user) {
        generatePasswordResetToken(user, null);
    }

    @Override
    @Transactional
    public void generatePasswordResetToken(Users user, String triggeredBy) {
        passwordResetTokenRepo.invalidateAllTokensForUser(user.getId());

        // The raw token travels only in the email link; only its hash is persisted.
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .passwordToken(hashToken(rawToken))
                .used(false)
                .createdAt(Instant.now())
                .expiryDate(Instant.now().plusMillis(resetExpirationMs()))
                .expiration(resetExpirationMs())
                .createdBy(triggeredBy)
                .user(user)
                .build();

        passwordResetTokenRepo.save(resetToken);

        // Le jeton BRUT ne voyage que par ce courriel : seule son empreinte est
        // persistée. Si l'envoi échoue, personne ne peut plus le retrouver — d'où le
        // journal de remise, qui rend l'échec visible et l'envoi rejouable.
        //
        // Asynchrone : la réponse ne doit pas attendre Microsoft. Un utilisateur qui
        // patiente recommence, et chaque demande invalide le jeton précédent.
        passwordResetMailService.sendPasswordResetMail(user, rawToken, resetExpirationMs());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTokenValid(String token) {
        return passwordResetTokenRepo.findByPasswordToken(hashToken(token))
                .map(t -> !Boolean.TRUE.equals(t.getUsed()) && t.getExpiryDate().isAfter(Instant.now()))
                .orElse(false);
    }

    @Override
    @Transactional
    public void validatePasswordResetToken(String token, String newPassword) {
        // Authoritative password strength guard · covers both the form and the JSON confirm API.
        passwordPolicyValidator.validateOrThrow(newPassword);

        String tokenHash = hashToken(token);
        PasswordResetToken resetToken = passwordResetTokenRepo.findByPasswordToken(tokenHash)
                .orElseThrow(() -> new BadRequestException("Lien de réinitialisation invalide ou expiré"));

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new BadRequestException("Ce lien de réinitialisation a déjà été utilisé");
        }

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            throw new BadRequestException("Ce lien de réinitialisation a expiré. Veuillez en demander un nouveau");
        }

        Users user = resetToken.getUser();
        String userEmail = user.getEmail();
        String triggeredBy = resetToken.getCreatedBy();

        userService.resetUserPassword(user, newPassword);
        passwordResetTokenRepo.invalidateToken(tokenHash);
        refreshTokenRepository.revokeActiveTokensByUserId(user.getId());

        user.setFailedLoginAttempts(0);
        user.setIsAccountLocked(false);

        log.info("Password successfully reset for user: {} (triggered by: {})",
                userEmail, triggeredBy != null ? triggeredBy : "self");

        // Fire async notifications after commit · non-blocking, no thread held
//        runAfterCommit(() -> {
//            notifier.sendPasswordChangedConfirmation(userEmail);
//            notifier.notifyAdminResetCompleted(triggeredBy, userEmail);
//        });
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * Hashes a reset token for storage/lookup. Reset tokens are high-entropy random UUIDs,
     * so a fast unsalted SHA-256 is appropriate: a database leak never exposes usable links,
     * yet lookup by hash stays deterministic.
     */
    private String hashToken(String token) {
        if (token == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
