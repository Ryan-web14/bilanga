package com.sni.bilanga.security.service.tokenService.implementation;

import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.security.admin.user.model.UserPrincipal;
import com.sni.bilanga.security.admin.user.model.Users;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
public class JWTService {

    private static final String CLAIM_SESSION_ID = "sessionId";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "type";

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String TOKEN_TYPE_VERIFICATION = "verification";
    private static final int MIN_HMAC_KEY_BYTES = 32;

    /**
     * Réglages du jeton, lus depuis {@code AppProperties}.
     *
     * Le secret avait auparavant pour valeur de repli une chaîne codée en dur :
     * une configuration muette suffisait à faire signer les jetons avec un
     * secret public, sans que rien ne le signale. Il n'a plus de repli, et
     * {@code ConfigurationGuard} refuse le démarrage en production s'il manque.
     */
    private final AppProperties.Security.Jwt jwt;

    public JWTService(AppProperties.Security security) {
        this.jwt = security.getJwt();
    }

    /** Conservé : la durée de vie du jeton de rafraîchissement est lue ailleurs. */
    public long getRefreshTokenExpiration() {
        return jwt.getRefreshTokenExpirationMs();
    }

    public String generateAccesToken(Users user, String sessionId) {
        return generateAccessToken(user, sessionId);
    }

    public String generateAccessToken(Users user, String sessionId) {
        return buildAccessToken(user, sessionId);
    }

    public String generateRefreshToken(Users user, String sessionId) {
        return buildSimpleToken(user, sessionId, jwt.getRefreshTokenExpirationMs(), TOKEN_TYPE_REFRESH);
    }

    public String generateVerificationToken(Users user, String sessionId) {
        return buildSimpleToken(user, sessionId, jwt.getVerificationTokenExpirationMs(), TOKEN_TYPE_VERIFICATION);
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    public boolean isTokenValid(String token, Users user) {
        String email = extractEmail(token);
        return email.equalsIgnoreCase(user.getEmail()) && !isTokenExpired(token);
    }

    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(extractTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(extractTokenType(token));
    }

    public boolean isVerificationToken(String token) {
        return TOKEN_TYPE_VERIFICATION.equals(extractTokenType(token));
    }

    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        validateNotExpired(claims);
        return claims.getSubject();
    }

    public String extractSessionId(String token) {
        Claims claims = extractAllClaims(token);
        validateNotExpired(claims);

        Object sessionId = claims.get(CLAIM_SESSION_ID);

        if (sessionId == null) {
            throw new JwtException("Session identifier missing from token");
        }

        return sessionId.toString();
    }

    public String extractTokenType(String token) {
        Claims claims = extractAllClaims(token);
        validateNotExpired(claims);

        Object type = claims.get(CLAIM_TYPE);

        if (type == null) {
            throw new JwtException("Token type missing from token");
        }

        return type.toString();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        validateNotExpired(claims);

        Object roles = claims.get(CLAIM_ROLES);

        if (roles == null) {
            return List.of();
        }

        if (!(roles instanceof List<?> rawRoles)) {
            throw new JwtException("Invalid roles claim format");
        }

        return rawRoles.stream()
                .map(Object::toString)
                .toList();
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new JwtException("Invalid JWT token", ex);
        }
    }

    private String buildAccessToken(Users user, String sessionId) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + jwt.getAccessTokenExpirationMs());

        UserPrincipal principal = new UserPrincipal(user);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_SESSION_ID, sessionId)
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_ROLES, principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList())
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Utilisé pour refresh token et verification token.
     * Ces tokens doivent rester légers : email + sessionId + type + dates.
     * Pas de roles/permissions ici.
     */
    private String buildSimpleToken(
            Users user,
            String sessionId,
            long expiration,
            String tokenType
    ) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_SESSION_ID, sessionId)
                .claim(CLAIM_TYPE, tokenType)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(getSigningKey())
                .compact();
    }

    private void validateNotExpired(Claims claims) {
        if (claims.getExpiration().before(new Date())) {
            throw new JwtException("Token is expired");
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwt.getSecret() == null ? new byte[0] : jwt.getSecret().trim().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length >= MIN_HMAC_KEY_BYTES) {
            return Keys.hmacShaKeyFor(keyBytes);
        }
        if (keyBytes.length == 0) {
            throw new IllegalStateException("JWT secret must not be empty");
        }
        return Keys.hmacShaKeyFor(sha256(keyBytes));
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
