package com.sni.bilanga.security.authentication.service.implementation;


import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.generator.verification.GeneratorOfVerificationCode;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.authentication.model.OneTimeToken;
import com.sni.bilanga.security.authentication.repository.OneTimeTokenRepository;
import com.sni.bilanga.security.authentication.service.interfaces.OneTimeTokenService;
import com.sni.bilanga.security.service.tokenService.implementation.JWTService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class OneTimeTokenServiceImpl implements OneTimeTokenService {

    private final OneTimeTokenRepository ottRepo;
   // private final OttMailService mailService;
    private final JWTService jwtService;
    private final AppProperties.Security securityConfig;

    /** Durée de vie d'un code à usage unique. */
    private long ottExpirationMs() {
        return securityConfig.getOneTimeToken().getExpirationMs();
    }



    @Override
    @Transactional
    public String generateOneTimeToken(Users user) {
        ottRepo.invalidateAllTokensForUser(user.getId());
        String token = GeneratorOfVerificationCode.generateVerificationCode(6);
        OneTimeToken ott = OneTimeToken.builder()
                .users(user)
                .isUsed(false)
                .createdAt(Instant.now())
                .token(token)
                .expiration(ottExpirationMs())
                .expiredAt(Instant.now().plusMillis(ottExpirationMs()))
                .build();
        ottRepo.save(ott);
       // mailService.sendOneTimeTokenMail(user, token);

        return jwtService.generateVerificationToken(user, "VerificationSession");
    }

    @Override
    @Transactional
    public Map<Boolean, String> validateOneTimeToken(String token, String verificationToken) {


        if(!ottRepo.isValidToken(token)){
            log.error("Token is not valid");
            return Map.of(false,"");
        }

        OneTimeToken ott = ottRepo.findByToken(token);
        String email = jwtService.extractEmail(verificationToken);

        if(email == null) {
            log.error("Error while extracting email from verification token");
            return Map.of(false, "");
        }

        if(!ott.getUsers().getEmail() .equalsIgnoreCase(email)){
            log.warn("Security Alert: Token ownership mismatch. User '{}' attempted to use token belonging to another user.",email);
            return Map.of(false, "");
        }

        if(ott.isUsed() || ott.getExpiredAt().isBefore(Instant.now())){
            log.error("Token validation failed: Invalid, expired, or already used.");
            return Map.of(false, "");
        }

        ott.setUsed(true);
        ottRepo.save(ott);
        return Map.of(true, email);
    }

    @Override
    public void deleteOneTimeToken(String token) {
        ottRepo.deleteByToken(token);
    }
}
