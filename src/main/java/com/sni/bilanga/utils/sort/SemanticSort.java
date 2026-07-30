package com.sni.bilanga.utils.sort;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import java.util.Map;

/**
 * Traduit un tri sur une colonne à vocabulaire fermé en tri sur son <em>rang</em>.
 *
 * <p>Les niveaux, priorités et statuts sont stockés en clair. Trier dessus
 * revenait donc à trier alphabétiquement : {@code ?sort=priority,desc} renvoyait
 * {@code MOYENNE, HAUTE, BASSE} — un ordre qui n'a aucun sens et que rien ne
 * signalait. Le client devait tout rapatrier pour retrier lui-même, ce qui ruine
 * l'intérêt de la pagination : la première page n'était pas la plus urgente.
 *
 * <p>La substitution a lieu ici, avant l'exécution : la propriété logique
 * demandée par l'appelant est remplacée par une expression {@code CASE} qui
 * classe les valeurs par gravité. L'API ne change pas — {@code ?sort=level,desc}
 * reste la façon de demander « les plus graves d'abord » — mais elle produit
 * enfin ce qu'elle promet.
 *
 * <p><strong>Convention de rang : plus la valeur est urgente, plus le rang est
 * élevé.</strong> {@code desc} donne donc les plus urgents d'abord, ce qui est
 * la lecture naturelle. Toute valeur hors vocabulaire tombe au rang 0.
 */
public final class SemanticSort {

    private SemanticSort() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * @param expressions propriété logique → expression JPQL de rang. L'expression
     *                    doit employer l'alias de la requête ({@code a}, {@code r}…).
     */
    public static Pageable rewrite(Pageable pageable, Map<String, String> expressions) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return pageable;
        }

        Sort rewritten = Sort.unsorted();
        boolean changed = false;

        for (Sort.Order order : pageable.getSort()) {
            String expression = expressions.get(order.getProperty());
            if (expression == null) {
                rewritten = rewritten.and(Sort.by(order));
            } else {
                rewritten = rewritten.and(JpaSort.unsafe(order.getDirection(), expression));
                changed = true;
            }
        }

        return changed
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), rewritten)
                : pageable;
    }

    /**
     * Construit l'expression de rang : la première valeur citée est la plus
     * urgente et reçoit le rang le plus élevé.
     */
    public static String rankExpression(String column, String... valuesFromMostUrgent) {
        StringBuilder sql = new StringBuilder("(case");
        int rank = valuesFromMostUrgent.length;

        for (String value : valuesFromMostUrgent) {
            sql.append(" when upper(").append(column).append(") = '")
               .append(value.toUpperCase(java.util.Locale.ROOT)).append("' then ").append(rank--);
        }
        return sql.append(" else 0 end)").toString();
    }
}
