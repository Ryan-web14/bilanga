package com.sni.bilanga.security.ratelimit;

import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    // Les quotas sont figés à la construction plutôt que lus à chaque requête :
    // ils ne changent pas en cours d'exécution, et les champs restent finaux.
    private final boolean enabled;

    private final int loginMaxRequests;
    private final long loginWindowSeconds;
    private final int otpMaxRequests;
    private final long otpWindowSeconds;
    private final int callbackMaxRequests;
    private final long callbackWindowSeconds;
    private final int adminMaxRequests;
    private final long adminWindowSeconds;

    public RateLimitingFilter(AppProperties.Security security) {
        AppProperties.Security.RateLimit rateLimit = security.getRateLimit();

        this.enabled = rateLimit.isEnabled();
        this.loginMaxRequests = rateLimit.getLogin().getMaxRequests();
        this.loginWindowSeconds = rateLimit.getLogin().getWindowSeconds();
        this.otpMaxRequests = rateLimit.getOtp().getMaxRequests();
        this.otpWindowSeconds = rateLimit.getOtp().getWindowSeconds();
        this.callbackMaxRequests = rateLimit.getCallback().getMaxRequests();
        this.callbackWindowSeconds = rateLimit.getCallback().getWindowSeconds();
        this.adminMaxRequests = rateLimit.getAdmin().getMaxRequests();
        this.adminWindowSeconds = rateLimit.getAdmin().getWindowSeconds();
    }

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        Rule rule = ruleFor(request);
        if (rule == null || allow(rule.key(), rule.maxRequests(), Duration.ofSeconds(rule.windowSeconds()))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write("""
                {"errorCode":"TOO_MANY_REQUESTS","status":429,"message":"Too many requests. Please try again later."}
                """);
    }

    private boolean allow(String key, int maxRequests, Duration duration) {
        Instant now = Instant.now();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new Window(1, now.plus(duration));
            }
            return new Window(current.count() + 1, current.expiresAt());
        });
        return window.count() <= maxRequests;
    }

    private Rule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI().toLowerCase(Locale.ROOT);
        String ip = clientIp(request);

        if (path.equals(ApiPath.V1 + "/auth/login")) {
            return new Rule("login:" + ip, loginMaxRequests, loginWindowSeconds);
        }
        if (path.startsWith(ApiPath.V1 + "/auth/ott/") || path.startsWith(ApiPath.V1 + "/auth/password-reset/")) {
            return new Rule("otp:" + ip + ":" + path, otpMaxRequests, otpWindowSeconds);
        }
        if (path.startsWith(ApiPath.V1 + "/payments/mobile-money/")
                && (path.contains("callback") || path.contains("refund-callback"))) {
            return new Rule("callback:" + ip + ":" + path, callbackMaxRequests, callbackWindowSeconds);
        }
        if (path.startsWith(ApiPath.V1)) {
            return new Rule("admin:" + ip, adminMaxRequests, adminWindowSeconds);
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private record Rule(String key, int maxRequests, long windowSeconds) {
    }

    private record Window(int count, Instant expiresAt) {
    }
}
