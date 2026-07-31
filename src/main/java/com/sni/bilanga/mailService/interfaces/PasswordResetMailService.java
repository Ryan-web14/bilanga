package com.sni.bilanga.mailService.interfaces;



import com.sni.bilanga.security.admin.user.model.Users;

import java.util.concurrent.CompletableFuture;

public interface PasswordResetMailService {

    /**
     * Sends the password reset email. The {@code rawToken} is the plaintext token embedded in
     * the reset link; only its hash is ever persisted, so it must be passed explicitly here.
     *
     * @param user         recipient
     * @param rawToken     plaintext reset token (goes into the link, never stored)
     * @param expirationMs token lifetime in milliseconds, used to render the expiry label
     */
    CompletableFuture<Boolean> sendPasswordResetMail(Users user, String rawToken, long expirationMs);
}
