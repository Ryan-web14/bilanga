package com.sni.bilanga.security.authentication.service.interfaces;


import com.sni.bilanga.security.admin.user.model.Users;

import java.util.Map;

public interface OneTimeTokenService {

    /**
     * Generates a one-time token for the specified user and send it to the user's email.
     *
     * @param user the user for whom the one-time token is to be generated
     * @return a jwt token as a string
     */
    String generateOneTimeToken(Users user);
    /**
     * Validates a one-time token against a provided verification token.
     *
     * @param token the one-time token to be validated
     * @param verificationToken the token used for verification
     * @return a map containing a boolean as a key and the associated message as a value; if the validation
     *         is successful, the boolean key will be {@code true} and the value will be the email associated
     *         with the token, otherwise the key will be {@code false} and the value will indicate the reason for failure
     */
    Map<Boolean, String> validateOneTimeToken(String token, String verificationToken);
    /**
     * Deletes a one-time token from the system.
     *
     * @param token the one-time token to be deleted
     */
    void deleteOneTimeToken(String token);
}
