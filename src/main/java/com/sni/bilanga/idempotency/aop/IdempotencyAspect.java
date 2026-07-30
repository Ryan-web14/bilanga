package com.sni.bilanga.idempotency.aop;

import com.sni.bilanga.exception.customs.BadRequestException;
import com.sni.bilanga.idempotency.service.interfaces.IdempotencyService;
import com.sni.bilanga.idempotency.service.model.IdempotencyExecutionResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyService idempotencyService;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return pjp.proceed();
        }

        String idempotencyKey = request.getHeader(idempotent.keyHeader());
        if ((idempotencyKey == null || idempotencyKey.isBlank()) && idempotent.required()) {
            throw new BadRequestException("Missing required header: " + idempotent.keyHeader());
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return pjp.proceed();
        }

        Object[] args = pjp.getArgs();
        Object requestPayload = idempotent.requestBodyArgIndex() >= 0 && idempotent.requestBodyArgIndex() < args.length
                ? args[idempotent.requestBodyArgIndex()]
                : Map.of(
                        "requestUri", request.getRequestURI(),
                        "httpMethod", request.getMethod(),
                        "queryString", request.getQueryString()
                );

        IdempotencyExecutionResult<Object> result = idempotencyService.execute(
                idempotent.operation(),
                idempotencyKey,
                requestPayload,
                Object.class,
                () -> {
                    try {
                        Object response = pjp.proceed();
                        return wrapResponse(response);
                    } catch (RuntimeException runtimeException) {
                        throw runtimeException;
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                }
        );

        return unwrapResponse(result.getPayload());
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private Object wrapResponse(Object response) {
        if (response instanceof ResponseEntity<?> entity) {
            return Map.of(
                    "responseEntity", true,
                    "status", entity.getStatusCode().value(),
                    "body", entity.getBody()
            );
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private Object unwrapResponse(Object payload) {
        if (payload instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("responseEntity"))) {
            Object status = map.get("status");
            int statusCode = status instanceof Number number ? number.intValue() : 200;
            return ResponseEntity.status(statusCode).body(map.get("body"));
        }
        return payload;
    }
}
