package com.sni.bilanga.exception;

import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.utils.trace.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class ErrorResponse {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorResponse.class);

    private final AppProperties appProperties;

    public ApiError build(String errorCode, HttpStatus status, String message,
                          HttpServletRequest request, Throwable ex, List<String> errors) {

        // Reprend l'identifiant posé par TraceIdFilter : c'est lui qui figure dans
        // les lignes de journal de la requête. En tirer un nouveau ici rendait le
        // traceId renvoyé au client introuvable côté serveur.
        String traceId = TraceIdFilter.current();
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        String path = request != null ? request.getRequestURI() : null;

        if (status.is5xxServerError()) {
            LOGGER.error("[traceId={}] {} {} · {}", traceId, errorCode, path, message, ex);
        } else {
            LOGGER.warn("[traceId={}] {} {} · {}", traceId, errorCode, path,
                    ex != null ? ex.getMessage() : message);
        }

        ApiError.ApiErrorBuilder builder = ApiError.builder()
                .success(false)
                .errorCode(errorCode)
                .status(status.value())
                .message(message)
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .errors(errors);

        if (appProperties.isDevMode()) {
            builder.path(path)
                    .debugMessage(ex != null ? buildDebugMessage(ex) : null)
                    .exceptionName(ex != null ? ex.getClass().getName() : null);
        }

        return builder.build();
    }

    private String buildDebugMessage(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage());
        Throwable cause = ex.getCause();
        while (cause != null) {
            sb.append(" → ").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
            cause = cause.getCause();
        }
        return sb.toString();
    }
}
