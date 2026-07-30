package com.sni.bilanga.audit.aop;



import com.sni.bilanga.audit.context.AuditContext;
import com.sni.bilanga.audit.context.SecurityAuditContextProvider;
import com.sni.bilanga.audit.enums.AuditStatus;
import com.sni.bilanga.audit.model.AuditLog;
import com.sni.bilanga.audit.service.interfaces.AuditService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
public class AspectAudit {

    private final AuditService auditService;
    private final SecurityAuditContextProvider ctx;

    @Around("@annotation(audited)")
    public Object Around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        HttpServletRequest request = currentRequest();
        Map<String, Object> metadata = new HashMap<>(AuditContext.getMeta());
        metadata.put("method", pjp.getSignature().toShortString());
        if (request != null) {
            metadata.put("requestUri", request.getRequestURI());
            metadata.put("httpMethod", request.getMethod());
            if (request.getQueryString() != null) {
                metadata.put("queryString", request.getQueryString());
            }
        }

        AuditLog log = AuditLog.builder()
                .createdAt(Instant.now())
                .action(audited.action())
                .ressource(blankToNull(audited.ressource()))
                .actorEmail(ctx.emailOrSystem())
                .actorId(ctx.userIdOrNull() == null ? 0L : ctx.userIdOrNull())
                .auditStatus(AuditStatus.SUCCESS)
                .module(audited.module())
                .ipAddress(request == null ? null : request.getRemoteAddr())
                .userAgent(request == null ? null : request.getHeader("User-Agent"))
                .sessionId(request == null ? null : (String) request.getAttribute("sessionId"))
                .metadata(metadata)
                .build();

        try{
            Object result = pjp.proceed();

            // ⚠️ Les métadonnées sont RELUES ici, et pas seulement au-dessus.
            //
            // Le défaut corrigé : la carte était figée AVANT pjp.proceed(), si bien
            // que tout AuditContext.putMeta appelé par la méthode auditée — c'est-à-
            // dire par le contrôleur lui-même, le seul endroit qui connaisse
            // l'objet concerné — était perdu. Les hooks d'enrichissement étaient
            // câblés, appelables, et sans effet : ce qu'on y écrivait n'arrivait
            // jamais en base.
            //
            // Le pré-remplissage du dessus reste utile pour l'URI, la méthode HTTP
            // et un éventuel putMeta posé par un intercepteur en amont ; la relecture
            // y ajoute ce que la méthode a appris en s'exécutant.
            metadata.putAll(AuditContext.getMeta());
            log.setMetadata(metadata);
            log.setDiff(AuditContext.getDiff());

            auditService.save(log);

            return result;
        }catch (Exception e){
            log.setAuditStatus(AuditStatus.FAILURE);
            log.setErrorCode(e.getClass().getSimpleName());
            log.setErrorMessage(safeMsg(e.getMessage()));

            // Même relecture sur le chemin d'échec : ce qui a été enrichi avant
            // que la méthode ne lève est précisément ce qui aide à comprendre
            // POURQUOI elle a levé.
            metadata.putAll(AuditContext.getMeta());
            metadata.put("exception", e.getClass().getSimpleName());
            log.setMetadata(metadata);
            log.setDiff(AuditContext.getDiff());

            auditService.save(log);
            throw e;
        }finally {
            // Impératif : le ThreadLocal survit à la requête sur un fil réutilisé
            // du pool servlet. Sans ce nettoyage, les métadonnées d'une opération
            // se retrouveraient attachées à la suivante — un audit qui attribue
            // un changement au mauvais objet est pire qu'un audit vide.
            AuditContext.clear();
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String safeMsg(String msg) {
        if (msg == null) return null;
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
