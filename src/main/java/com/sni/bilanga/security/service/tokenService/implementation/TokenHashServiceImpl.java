package com.sni.bilanga.security.service.tokenService.implementation;

import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.security.service.tokenService.interfaces.TokenHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TokenHashServiceImpl implements TokenHashService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";


    private final AppProperties.Security securityConfig;

    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or blank");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            SecretKeySpec keySpec = new SecretKeySpec(
                    securityConfig.getTokenHash().getSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );

            mac.init(keySpec);

            byte[] hash = mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (Exception e) {
            throw new IllegalStateException("Could not hash token with HMAC-SHA256", e);
        }
    }

    public boolean matches(String rawToken, String storedHash) {
        if (rawToken == null || storedHash == null) {
            return false;
        }

        String computedHash = hash(rawToken);

        return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
