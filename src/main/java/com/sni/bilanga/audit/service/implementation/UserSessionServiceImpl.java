package com.sni.bilanga.audit.service.implementation;


import com.sni.bilanga.audit.dto.response.UserSessionResponse;
import com.sni.bilanga.audit.dto.response.UserSessionToken;
import com.sni.bilanga.audit.mapper.interfaces.UserSessionMapper;
import com.sni.bilanga.audit.model.UserSession;
import com.sni.bilanga.audit.repository.UserSessionRepository;
import com.sni.bilanga.audit.service.interfaces.UserSessionService;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.security.admin.user.model.UserPrincipal;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.service.tokenService.implementation.JWTService;
import com.sni.bilanga.security.service.tokenService.interfaces.RefreshTokenService;
import com.sni.bilanga.security.service.tokenService.interfaces.TokenHashService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua_parser.Client;
import ua_parser.Parser;

import java.io.File;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserSessionServiceImpl implements UserSessionService {

    private final UserSessionRepository sessionRepo;
    private final UserSessionMapper sessionMapper;
    private final JWTService jwtService;
    private final RefreshTokenService tokenService;
    private final TokenHashService tokenHashService;

    @Transactional
    @Override
    public UserSessionToken createSession(HttpServletRequest request, UserPrincipal user) {

        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String deviceType = resolveDeviceType(userAgent);
        String location = "no localation";

        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        String accessToken = jwtService.generateAccessToken(user.getUser(), sessionId.toString());
        String refreshToken = jwtService.generateRefreshToken(user.getUser(), sessionId.toString());

        String refreshTokenHash = tokenHashService.hash(refreshToken);

        tokenService.storeRefreshToken(refreshToken, user.getUser());

        UserSession session = UserSession.builder()
                .sessionId(sessionId)
                .users(user.getUser())
                .refreshTokenHash(refreshTokenHash)
                .createdAt(now)
                .expiredAt(now.plusMillis(jwtService.getRefreshTokenExpiration()))
                .lastAccessAt(now)
                .expiresIn(jwtService.getRefreshTokenExpiration())
                .ipAddress(clientIp)
                .userAgent(userAgent)
                .deviceType(deviceType)
                .isActive(true)
                .revoked(false)
                .roleSnapshot(buildRoleSnapshot(user))
                .locationGuess(location)
                .build();

        sessionRepo.save(session);

        return new UserSessionToken(accessToken, refreshToken);
    }

    @Override
    public void deleteSessionByToken(String token) {
        UUID sessionId = extractSessionId(token);
        log.debug("Deleting session by token: {}", sessionId);
        sessionRepo.deleteBySessionId(sessionId);
    }

    @Override
    public void deleteAllSessions(Users users) {
        log.debug("Deleting all sessions for user {}", users.getEmail());
        sessionRepo.deleteByUsers(users);
    }

    @Override
    public void deleteOldestSession(Users user) {
        List<UserSession> sessions =
                sessionRepo.findAllByUsers_IdAndRevokedFalseOrderByCreatedAtDesc(user.getId());

        if (sessions.size() > 1) {
            log.debug("Deleting oldest session for user {}", user.getEmail());
            UserSession oldest = sessions.get(sessions.size() - 1);
            sessionRepo.delete(oldest);
        }
    }

    @Override
    public void invalidateAllSessions() {
        List<UserSession> sessions = sessionRepo.findAll();
        sessions.forEach(this::markRevoked);
        sessionRepo.saveAll(sessions);
    }

    @Override
    public void invalidateSessionByToken(String token) {
        UUID sessionId = extractSessionId(token);

        UserSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("No such session exist"));

        markRevoked(session);
        sessionRepo.save(session);
    }

    @Override
    public void invalidateSessionByUser(Users user) {
        List<UserSession> sessions =
                sessionRepo.findAllByUsers_IdOrderByCreatedAtAsc(user.getId());

        sessions.forEach(this::markRevoked);
        sessionRepo.saveAll(sessions);
    }

    @Override
    public void invalidateOldestSessionByUser(Users user) {
        List<UserSession> sessions =
                sessionRepo.findAllByUsers_IdAndRevokedFalseOrderByCreatedAtDesc(user.getId());

        if (sessions.size() > 1) {
            log.debug("Invalidating oldest session for user {}", user.getEmail());
            UserSession oldest = sessions.get(sessions.size() - 1);
            markRevoked(oldest);
            sessionRepo.save(oldest);
        }
    }

    @Override
    public List<UserSessionResponse> getAllSessions() {
        return sessionRepo.findAll()
                .stream()
                .map(sessionMapper::toDTO)
                .toList();
    }

    @Override
    public List<UserSessionResponse> getAllActiveSessionsForUser(Users user) {
        return sessionRepo.findAllByUsers_IdAndRevokedFalseAndIsActiveTrueOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(sessionMapper::toDTO)
                .toList();
    }

    @Override
    public UserSessionResponse getCurrentSession(String token) {
        return sessionMapper.toDTO(
                sessionRepo.findBySessionId(extractSessionId(token))
                        .orElseThrow(() -> new ResourceNotFoundException("No such session exist"))
        );
    }

    @Override
    public void refreshSession(String sessionId, String token) {
        try {
            log.debug("Refreshing session {}", sessionId);

            UUID sessionUUID = UUID.fromString(sessionId);

            UserSession session = sessionRepo.findBySessionId(sessionUUID)
                    .orElseThrow(() -> new ResourceNotFoundException("No such session exist"));

            String refreshTokenHash = tokenHashService.hash(token);
            Instant now = Instant.now();

            session.setRefreshTokenHash(refreshTokenHash);
            session.setExpiredAt(now.plusMillis(jwtService.getRefreshTokenExpiration()));
            session.setLastAccessAt(now);
            session.setActive(true);
            session.setRevoked(false);

            sessionRepo.save(session);

        } catch (IllegalArgumentException e) {
            log.error("Invalid session id {}: {}", sessionId, e.getMessage());
        } catch (ResourceNotFoundException e) {
            log.error("Session {} was not found", sessionId);
        }
    }

    @Override
    public UserSession getSessionByTokenService(String token) {
        return sessionRepo.findBySessionId(extractSessionId(token))
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
    }

    @Override
    public boolean isSessionValid(String token) {
        UserSession session = getSessionByTokenService(token);

        return !session.isRevoked()
                && session.isActive()
                && session.getExpiredAt() != null
                && session.getExpiredAt().isAfter(Instant.now());
    }

    @Override
    public boolean isSessionRevoked(String sessionId) {
        try {
            UUID sessionUUID = UUID.fromString(sessionId);

            return sessionRepo.findBySessionId(sessionUUID)
                    .map(session -> session.isRevoked()
                            || !session.isActive()
                            || session.getExpiredAt() == null
                            || session.getExpiredAt().isBefore(Instant.now()))
                    .orElse(true);

        } catch (IllegalArgumentException ex) {
            return true;
        }
    }

    private String resolveDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }

        try {
            Parser parser = new Parser();
            Client client = parser.parse(userAgent);

            if (client.device == null || client.device.family == null || client.device.family.isBlank()) {
                return "Other";
            }

            return client.device.family;

        } catch (Exception e) {
            log.debug("Unable to parse user agent: {}", e.getMessage());
            return "Other";
        }
    }


    private String buildRoleSnapshot(UserPrincipal user) {
        return user.getAuthorities()
                .stream()
                .map(Object::toString)
                .sorted()
                .limit(3)
                .collect(Collectors.joining(","));
    }

    private String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");

        if (ipAddress != null && !ipAddress.isBlank() && !"unknown".equalsIgnoreCase(ipAddress)) {
            return ipAddress.split(",")[0].trim();
        }

        ipAddress = request.getHeader("Forwarded");

        if (ipAddress != null && !ipAddress.isBlank() && !"unknown".equalsIgnoreCase(ipAddress)) {
            return ipAddress;
        }

        return request.getRemoteAddr();
    }

//    private String getLocationByIp(String clientIp) {
//        DatabaseReader reader = null;
//
//        try {
//            File db = new File("C:/java-project/ohada-bokati/addon-resources/GeoLite2-City_20251125");
//
//            reader = new DatabaseReader.Builder(db)
//                    .withCache(new CHMCache())
//                    .build();
//
//            InetAddress ipAddress = InetAddress.getByName(clientIp);
//            CityResponse cityResponse = reader.city(ipAddress);
//
//            Country country = cityResponse.country();
//
//            String isoCode = country.isoCode();
//            String countryName = country.name();
//            String city = cityResponse.city().name();
//
//            Double latitude = cityResponse.location().latitude();
//            Double longitude = cityResponse.location().longitude();
//
//            return city + ", " + countryName + ", " + isoCode + ", " + latitude + ", " + longitude;
//
//        } catch (Exception e) {
//            log.error("Error getting location by ip: {}", e.getMessage());
//        } finally {
//            if (reader != null) {
//                try {
//                    reader.close();
//                } catch (Exception e) {
//                    log.error("Error closing database reader: {}", e.getMessage());
//                }
//            }
//        }
//
//        return "no location";
//    }
//
    private UUID extractSessionId(String token) {
        try {
            return UUID.fromString(jwtService.extractSessionId(token));
        } catch (Exception ex) {
            throw new ResourceNotFoundException("Session not found");
        }
    }

    private void markRevoked(UserSession session) {
        Instant now = Instant.now();

        session.setRevoked(true);
        session.setActive(false);
        session.setLastAccessAt(now);
    }
}