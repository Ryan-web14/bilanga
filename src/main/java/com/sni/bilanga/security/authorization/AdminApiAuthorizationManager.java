package com.sni.bilanga.security.authorization;

import com.sni.bilanga.utils.path.ApiPath;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Contrôle d'accès par URL pour l'ensemble de {@code /sni/api/v1/**}.
 *
 * <p><strong>Ce n'est pas un gestionnaire « admin ».</strong> Le nom est
 * historique : {@code SecurityConfig} le branche sur <em>toutes</em> les routes
 * de l'API, pas seulement sur {@code /admin/**}. Il décide donc aussi bien de
 * l'accès d'un exploitant à ses parcelles que de celui d'un administrateur aux
 * journaux.
 *
 * <p><strong>Deux défauts corrigés dans cette réécriture</strong>, l'un et
 * l'autre invisibles tant que le {@code permitAll("/**")} de
 * {@code SecurityConfig} court-circuite cette classe — mais fatals le jour où il
 * sautera :
 *
 * <ol>
 *   <li><strong>Seuls {@code ADMIN} et {@code SUPER_ADMIN} passaient.</strong>
 *       Un contrôle préalable exigeait l'un de ces deux rôles avant même de
 *       regarder la permission demandée. Un exploitant se serait vu refuser
 *       <em>toutes</em> les routes, y compris celles de ses propres parcelles.
 *       Sur une plateforme agricole, cela rendait le produit inutilisable pour
 *       son utilisateur principal.</li>
 *   <li><strong>{@code /ingest/**} aurait été refusé.</strong> Les boîtiers
 *       s'authentifient par clé partagée dans l'en-tête {@code X-Device-Key},
 *       jamais par jeton : le premier test — « la requête est-elle
 *       authentifiée ? » — les écartait tous. Toute l'ingestion de terrain se
 *       serait arrêtée.</li>
 * </ol>
 *
 * <p><strong>La cartographie a été refaite pour Bilanga.</strong> L'ancienne
 * dérivait des permissions {@code BILLING}, {@code PAYMENT}, {@code KYC},
 * {@code CASH}, {@code BOOKING}, {@code SUBSCRIPTION}, {@code INVENTORY},
 * {@code VISITOR}… — une trentaine de modules d'un projet de finance dont
 * <em>aucun</em> n'a de route ici. Elle décrivait un système qui n'existe pas,
 * et taisait celui qui existe.
 *
 * <p><strong>⚠️ Refus par défaut.</strong> Une route non cartographiée est
 * <em>refusée</em>, pas laissée passer. C'est délibéré : l'inverse ferait
 * qu'un contrôleur nouvellement ajouté serait ouvert à tout compte authentifié,
 * silencieusement. Ici l'oubli produit un 403 bruyant, qu'on remarque tout de
 * suite.
 *
 * <p><strong>Conséquence pour qui ajoute un contrôleur : ajouter sa ligne dans
 * {@link #resolvePermission}.</strong> Sans quoi la route répondra 403 dès que
 * le contrôle sera actif.
 */
@Component
public class AdminApiAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    /**
     * Routes portant leur propre authentification, en amont de Spring Security.
     *
     * {@code IngestController} vérifie lui-même {@code X-Device-Key} avec une
     * comparaison à durée constante. Exiger en plus un jeton reviendrait à
     * demander à un microcontrôleur de gérer un cycle de vie d'OAuth.
     */
    private static final List<String> DEVICE_AUTHENTICATED = List.of("/ingest");

    /**
     * Routes accessibles à tout compte authentifié, quel que soit son rôle.
     *
     * Elles ne donnent accès qu'à ses propres données : refuser à quelqu'un de
     * lire son profil ou de régler ses notifications n'aurait aucun sens.
     */
    private static final List<String> SELF_SERVICE = List.of(
            "/auth/me",
            "/auth/logout",
            "/notifications/preferences");

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {

        String path = normalizePath(context.getRequest().getRequestURI());

        // AVANT toute question d'authentification : le matériel de terrain ne
        // porte pas de jeton, et son contrôleur assure sa propre garde.
        if (startsWithAny(path, DEVICE_AUTHENTICATED)) {
            return new AuthorizationDecision(true);
        }

        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();

        // Le super administrateur passe avant la cartographie : c'est le seul
        // recours si une route venait à manquer dans la table.
        if (has(authorities, SecurityRole.SUPER_ADMIN.authority())) {
            return new AuthorizationDecision(true);
        }

        if (startsWithAny(path, SELF_SERVICE)) {
            return new AuthorizationDecision(true);
        }

        AppPermission required = resolvePermission(path, context.getRequest().getMethod());

        // Route non cartographiée : refus, sauf laissez-passer explicite.
        // Un oubli doit se voir, pas s'ouvrir.
        if (required == null) {
            return new AuthorizationDecision(has(authorities, AppPermission.ADMIN_ACCESS.authority()));
        }

        return new AuthorizationDecision(has(authorities, required.authority()));
    }

    // ============================================================
    // Cartographie des routes de Bilanga
    // ============================================================

    /**
     * Permission exigée par une route, ou {@code null} si elle n'est pas
     * cartographiée.
     *
     * <p>L'ordre compte : les cas particuliers précèdent les préfixes généraux.
     */
    private AppPermission resolvePermission(String path, String method) {

        // ── Attributions ────────────────────────────────────────
        //
        // RoleAssignmentAdminController est à cheval sur deux préfixes, et ses
        // @PreAuthorize ne suivent PAS celui du chemin : attribuer un rôle à un
        // utilisateur relève de SYSTEM:ROLES bien que la route commence par
        // /admin/users, et attribuer des permissions à un rôle relève de
        // SYSTEM:PERMISSIONS bien qu'elle commence par /admin/roles.
        //
        // Ces deux lignes doivent donc précéder les préfixes généraux. Sans
        // elles, la couche URL était PLUS STRICTE que le contrat documenté : un
        // compte porteur de SYSTEM:PERMISSIONS mais non de SYSTEM:ROLES se
        // voyait refuser l'écran d'attribution des permissions — avant même que
        // @PreAuthorize, qui l'aurait accepté, ne soit consulté.
        //
        // Invisible aujourd'hui, parce que ADMIN et SUPER_ADMIN portent les deux
        // permissions et que le permitAll fourre-tout court-circuite cette
        // classe. Fatal pour tout rôle sur mesure — un « gestionnaire de droits »
        // par exemple — le jour du durcissement.
        if (path.startsWith("/admin/users/") && path.contains("/roles")) {
            return AppPermission.SYSTEM_ROLES;
        }
        if (path.startsWith("/admin/roles/")
                && (path.contains("/permissions") || path.contains("/permission-names"))) {
            return AppPermission.SYSTEM_PERMISSIONS;
        }

        // ── Administration système ──────────────────────────────
        if (path.startsWith("/admin/users") || path.startsWith("/admin/provisioning")) {
            return AppPermission.SYSTEM_USERS;
        }
        if (path.startsWith("/admin/roles")) {
            return AppPermission.SYSTEM_ROLES;
        }
        if (path.startsWith("/admin/permissions")) {
            return AppPermission.SYSTEM_PERMISSIONS;
        }
        if (path.startsWith("/admin/audit-logs")
                || path.startsWith("/admin/settings-audit-logs")
                || path.startsWith("/admin/idempotency-records")) {
            return AppPermission.SYSTEM_AUDIT;
        }
        if (path.startsWith("/admin/notifications")) {
            return AppPermission.SYSTEM_NOTIFICATIONS;
        }
        if (path.startsWith("/admin/cooperatives") || path.startsWith("/admin/farms")) {
            return organization(method);
        }
        if (path.startsWith("/admin/")) {
            return AppPermission.SYSTEM_SETTINGS;
        }

        // ── Parcelles et cultures ───────────────────────────────
        //
        // Le bilan économique est rattaché à HARVEST et non à FARM : il expose
        // des marges et des prix de vente, que le rôle chargé du parc ou du
        // suivi agronomique n'a pas à consulter. Le second verrou — par rôle
        // d'exploitation — est posé par AccessGuard.requireScope(ECONOMIQUE).
        if (path.startsWith("/plots") && path.contains("/economics")) {
            return AppPermission.HARVEST_READ;
        }
        if (path.startsWith("/plots") || path.startsWith("/crops")) {
            return farm(method);
        }

        // ── Matériel de terrain ─────────────────────────────────
        if (path.startsWith("/devices") || path.startsWith("/sensors")
                || path.startsWith("/readings") || path.startsWith("/observations")) {
            return iot(method);
        }

        // ── Diagnostic, conseils, alertes ───────────────────────
        //
        // Acquitter, résoudre, affecter ou donner suite à un conseil sont des
        // PATCH : ils relèvent de UPDATE, pas de CREATE. Seul le lancement d'un
        // diagnostic crée quelque chose.
        if (path.startsWith("/diagnosis")) {
            return diagnosis(method);
        }
        if (path.startsWith("/alerts") || path.startsWith("/recommendations")) {
            return HttpMethod.GET.matches(method)
                    ? AppPermission.DIAGNOSIS_READ
                    : AppPermission.DIAGNOSIS_UPDATE;
        }

        // ── Base de connaissance ────────────────────────────────
        if (path.startsWith("/knowledge")) {
            return knowledge(method);
        }

        // ── Journal des actions ─────────────────────────────────
        if (path.startsWith("/interventions")) {
            return intervention(method);
        }
        if (path.startsWith("/harvests")) {
            return harvest(method);
        }

        // ── Tableaux de bord ────────────────────────────────────
        if (path.startsWith("/overview/economics")) {
            return AppPermission.HARVEST_READ;
        }
        if (path.startsWith("/overview")) {
            return AppPermission.OVERVIEW_READ;
        }

        // ── Notifications personnelles ──────────────────────────
        // /notifications/preferences est déjà traité en self-service.
        if (path.startsWith("/notifications")) {
            return AppPermission.SYSTEM_NOTIFICATIONS;
        }

        return null;
    }

    // ============================================================
    // Déclinaison par verbe HTTP
    // ============================================================
    private AppPermission organization(String method) {
        return byAction(method, AppPermission.ORGANIZATION_READ, AppPermission.ORGANIZATION_CREATE,
                AppPermission.ORGANIZATION_UPDATE, AppPermission.ORGANIZATION_DELETE);
    }

    private AppPermission farm(String method) {
        return byAction(method, AppPermission.FARM_READ, AppPermission.FARM_CREATE,
                AppPermission.FARM_UPDATE, AppPermission.FARM_DELETE);
    }

    private AppPermission iot(String method) {
        return byAction(method, AppPermission.IOT_READ, AppPermission.IOT_CREATE,
                AppPermission.IOT_UPDATE, AppPermission.IOT_DELETE);
    }

    private AppPermission diagnosis(String method) {
        return byAction(method, AppPermission.DIAGNOSIS_READ, AppPermission.DIAGNOSIS_CREATE,
                AppPermission.DIAGNOSIS_UPDATE, AppPermission.DIAGNOSIS_DELETE);
    }

    private AppPermission knowledge(String method) {
        return byAction(method, AppPermission.KNOWLEDGE_READ, AppPermission.KNOWLEDGE_CREATE,
                AppPermission.KNOWLEDGE_UPDATE, AppPermission.KNOWLEDGE_DELETE);
    }

    private AppPermission intervention(String method) {
        return byAction(method, AppPermission.INTERVENTION_READ, AppPermission.INTERVENTION_CREATE,
                AppPermission.INTERVENTION_UPDATE, AppPermission.INTERVENTION_DELETE);
    }

    private AppPermission harvest(String method) {
        return byAction(method, AppPermission.HARVEST_READ, AppPermission.HARVEST_CREATE,
                AppPermission.HARVEST_UPDATE, AppPermission.HARVEST_DELETE);
    }

    /** {@code PUT} et {@code PATCH} relèvent tous deux de {@code UPDATE}. */
    private AppPermission byAction(String method, AppPermission read, AppPermission create,
                                   AppPermission update, AppPermission delete) {
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)) return read;
        if (HttpMethod.POST.matches(method)) return create;
        if (HttpMethod.DELETE.matches(method)) return delete;
        return update;
    }

    // ============================================================
    // Interne
    // ============================================================

    /** Retire le préfixe d'API et normalise la casse. */
    private String normalizePath(String requestUri) {
        String uri = requestUri == null ? "" : requestUri;
        if (uri.startsWith(ApiPath.V1)) {
            uri = uri.substring(ApiPath.V1.length());
        }
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        return uri.toLowerCase(Locale.ROOT);
    }

    private boolean startsWithAny(String path, List<String> prefixes) {
        return prefixes.stream().anyMatch(path::startsWith);
    }

    private boolean has(Collection<? extends GrantedAuthority> authorities, String authority) {
        return authorities.stream().anyMatch(item -> authority.equals(item.getAuthority()));
    }
}
