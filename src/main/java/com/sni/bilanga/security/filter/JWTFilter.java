package com.sni.bilanga.security.filter;

import com.sni.bilanga.config.properties.AppProperties;
import com.auth0.jwt.exceptions.JWTVerificationException;

import com.sni.bilanga.audit.service.interfaces.UserSessionService;
import com.sni.bilanga.security.admin.user.model.UserPrincipal;
import com.sni.bilanga.security.service.tokenService.implementation.JWTService;
import com.sni.bilanga.security.service.user.CustomUserDetailService;
import com.sni.bilanga.utils.path.ApiPath;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class JWTFilter extends OncePerRequestFilter {

    private final JWTService jwtService;
    private final CustomUserDetailService userDetailService;
    private final UserSessionService sessionService;
    private static final String BEARER_PREFIX = "Bearer ";

    private final AppProperties appProperties;
    private final AppProperties.Security securityConfig;

    /**
     * La dérogation sur {@code /ws} a été retirée au lot 5 : le projet n'a aucun
     * point d'entrée WebSocket, et la dépendance correspondante a été supprimée
     * du {@code pom.xml}. Une règle qui protège un chemin inexistant laisse
     * croire qu'un canal temps réel existe.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isPublicApiRequest(request);
    }

    @SuppressWarnings("null")
    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain) throws ServletException, IOException {

            try {
                String token = extractToken(request);
                if (token == null) {
                    authenticateAutoAdmin();
                    filterChain.doFilter(request, response);
                    return;
                }

                String sessionId  = jwtService.extractSessionId(token);
                request.setAttribute("sessionId", sessionId);
                if(sessionService.isSessionRevoked(sessionId)) {
                    logger.debug("Session revoked or missing for token, skipping authentication");
                    filterChain.doFilter(request, response);
                    return;
                }

                processToken(token);
                filterChain.doFilter(request, response);

            } catch (ExpiredJwtException e) {
                handleJwtAuthenticationFailure(request, response, filterChain, "JWT token expired", "Expired JWT token");
            } catch (JwtException | JWTVerificationException e) {
                handleJwtAuthenticationFailure(request, response, filterChain, "Invalid JWT Token", "Invalid JWT token");
            } catch (Exception e) {
                logger.error("Error processing JWT token", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing JWT token");
            }
    }

    private void handleJwtAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            String responseMessage,
            String logMessage
    ) throws IOException, ServletException {
        SecurityContextHolder.clearContext();

        if (isPublicApiRequest(request)) {
            logger.debug(logMessage + " ignored for public endpoint " + request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        logger.debug(logMessage + " for " + request.getRequestURI());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, responseMessage);
    }


    private void processToken(String token){
        String email = jwtService.extractEmail(token);

        if(email != null && SecurityContextHolder.getContext().getAuthentication() == null){
            setAuthenticatedPrincipal((UserPrincipal) userDetailService.loadUserByUsername(email));
        }
    }

    private void authenticateAutoAdmin() {
        if (!securityConfig.getAutoAdmin().isEnabled() || SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        if (!StringUtils.hasText(securityConfig.getAutoAdmin().getEmail())) {
            logger.warn("Auto admin authentication is enabled but app.security.auto-admin.email is empty");
            return;
        }
        try {
            UserPrincipal userPrincipal = (UserPrincipal) userDetailService.loadUserByUsername(securityConfig.getAutoAdmin().getEmail().trim());
            setAuthenticatedPrincipal(userPrincipal);
            logger.debug("Authenticated user: " + userPrincipal.getUser().getEmail());
        } catch (Exception ex) {
            logger.warn("Unable to load auto admin user " + securityConfig.getAutoAdmin().getEmail(), ex);
        }
    }

    private void setAuthenticatedPrincipal(UserPrincipal userPrincipal) {
        UsernamePasswordAuthenticationToken authentification = new UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                userPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentification);
    }

    public String extractToken(HttpServletRequest request) {
        String authBearer = request.getHeader("Authorization");

        if(authBearer != null && authBearer.startsWith(BEARER_PREFIX)){
            String token = authBearer.substring(BEARER_PREFIX.length());

            if(token.isBlank()){
                logger.warn("Invalid token format: Bearer");
                return null;
            }
            return token;
        }
        return null;
    }

    /**
     * Routes par lesquelles on entre, et qui ne peuvent donc pas exiger un jeton.
     *
     * <p><strong>Nettoyage du lot 5 (A8).</strong> Cette liste déclarait aussi
     * publiques une dizaine de routes qui n'existent pas : les rappels
     * mobile-money de pawaPay (sous deux orthographes, {@code pawapay} et
     * {@code pawaypay}), {@code /client/catalog/plans}, {@code /shares/},
     * {@code /verify/} et l'aperçu de documents signés. Toutes héritées du
     * projet de finance.
     *
     * <p>Elles ne créaient pas de faille — la route absente répond 404 — mais
     * elles décrivaient une surface d'API qui n'existe pas, dans le fichier
     * précisément qu'on ouvre pour savoir ce qui est public. C'est le pire endroit
     * pour une affirmation fausse.
     */
    private boolean isPublicApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (
                uri.equals(ApiPath.V1 + "/auth/login")
                        || uri.equals(ApiPath.V1 + "/auth/refresh")
                        || uri.equals(ApiPath.V1 + "/auth/register")
                        || uri.startsWith(ApiPath.V1 + "/auth/ott/")
                        || uri.startsWith(ApiPath.V1 + "/auth/password-reset/")
                        || uri.equals(ApiPath.V1 + "/auth/unlock-account")
                        || uri.equals(ApiPath.V1 + "/auth/unlock-account/confirm")
                        || uri.equals(ApiPath.V1 + "/auth/email/verify/resend")

                        // Ingestion : authentifiée par clé partagée dans le
                        // contrôleur, avec comparaison à durée constante. Exiger en
                        // plus un jeton reviendrait à demander à un microcontrôleur
                        // de gérer un cycle de vie d'OAuth.
                        || uri.startsWith(ApiPath.V1 + "/ingest/")
        );
    }
}
