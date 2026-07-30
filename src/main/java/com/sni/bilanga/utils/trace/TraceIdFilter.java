package com.sni.bilanga.utils.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Attribue un identifiant de corrélation à chaque requête.
 *
 * {@code ApiError} portait déjà un {@code traceId}, mais il était tiré au hasard
 * au moment de construire l'erreur : l'identifiant remonté par un utilisateur ne
 * correspondait à aucune ligne de journal, et ne servait donc à rien. Il est
 * désormais posé en amont, écrit dans le MDC — donc dans toutes les lignes de
 * journal de la requête — et renvoyé en en-tête pour que le frontend puisse
 * l'afficher.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String traceId = resolve(request);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Les fils sont réutilisés d'une requête à l'autre : ne pas nettoyer
            // ferait porter à la suivante l'identifiant de la précédente.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Un identifiant fourni par l'appelant est conservé : c'est ce qui permet de
     * suivre une même opération depuis le frontend jusqu'ici.
     */
    private String resolve(HttpServletRequest request) {
        String provided = request.getHeader(HEADER);
        if (provided != null && !provided.isBlank() && provided.length() <= 64) {
            return provided.trim();
        }
        return UUID.randomUUID().toString();
    }

    /** Identifiant de la requête en cours, ou {@code null} hors contexte web. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
