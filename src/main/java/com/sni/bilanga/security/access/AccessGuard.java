package com.sni.bilanga.security.access;

import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.enums.AccessScope;
import com.sni.bilanga.enums.MembershipRole;
import com.sni.bilanga.exception.customs.ForbiddenException;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.organization.repository.FarmMembershipRepository;
import com.sni.bilanga.security.admin.user.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Point de contrôle unique de l'accès aux données d'une exploitation.
 *
 * <p><strong>Le défaut qu'il corrige.</strong> Rien ne rattachait une requête à
 * un propriétaire : {@code GET /plots?userId=42} renvoyait les parcelles de
 * l'utilisateur 42 à quiconque le demandait, et {@code GET /plots/{id}}
 * n'importe quelle parcelle. Le paramètre était pris pour argent comptant.
 *
 * <p><strong>Pourquoi tout passe par ici.</strong> Le cloisonnement pourrait
 * s'écrire dans chaque service ; il serait alors à réécrire partout le jour où
 * la notion de propriétaire s'élargit. Ce jour est arrivé avec la migration V22,
 * et le pari a tenu : l'élargissement aux exploitations et aux coopératives
 * s'écrit ici, et nulle part ailleurs.
 *
 * <p><strong>La règle appliquée.</strong>
 * <ul>
 *   <li>utilisateur privilégié ({@code ADMIN}, {@code SUPER_ADMIN}) — aucune restriction ;</li>
 *   <li><strong>propriétaire direct</strong> de la parcelle — accès complet, inconditionnel ;</li>
 *   <li><strong>membre de l'exploitation</strong> à laquelle la parcelle est rattachée —
 *       accès modulé par son rôle ({@link AccessScope}) ;</li>
 *   <li>utilisateur ordinaire sans lien — refusé ;</li>
 *   <li>appelant non authentifié — refusé.</li>
 * </ul>
 *
 * <p><strong>L'organisation n'est jamais bloquante.</strong> C'est la propriété
 * la plus importante de cette classe, et elle est intentionnelle :
 * <ul>
 *   <li>une parcelle <em>sans</em> exploitation se comporte exactement comme
 *       avant la V22 — la seule question posée est celle de la propriété
 *       directe, et aucune requête supplémentaire n'est même émise ;</li>
 *   <li>l'appartenance à une exploitation <em>ajoute</em> un accès, elle n'en
 *       retire aucun : le propriétaire direct garde le sien même s'il n'est pas
 *       membre de l'exploitation à laquelle sa parcelle est rattachée ;</li>
 *   <li>une exploitation mal configurée, ou vide de membres, ne peut donc
 *       enfermer personne dehors.</li>
 * </ul>
 *
 * <p>L'ensemble reste inactif tant que {@code app.security.ownership.enabled}
 * demeure à {@code false} : voir la justification sur la propriété elle-même.
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private static final Set<String> PRIVILEGED_ROLES = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");

    private final AppProperties appProperties;
    private final FarmMembershipRepository farmMembershipRepository;

    public boolean isEnabled() {
        return appProperties.getSecurity().getOwnership().isEnabled();
    }

    /**
     * Identifiant du propriétaire à retenir pour une recherche.
     *
     * @param requestedUserId ce que l'appelant a demandé — ignoré s'il n'a pas
     *                        le droit de consulter les données d'autrui
     * @return le filtre à appliquer, ou {@code null} pour « sans restriction »
     */
    public Long resolveOwnerFilter(Long requestedUserId) {
        if (!isEnabled() || isPrivileged()) {
            return requestedUserId;
        }
        return currentUserId();   // le paramètre transmis est délibérément écarté
    }

    /**
     * Exploitations dont l'appelant est membre.
     *
     * <p>Complète {@link #resolveOwnerFilter} pour les recherches : un
     * conseiller doit voir les parcelles de l'exploitation qu'il suit, dont il
     * n'est pourtant propriétaire d'aucune. Vide si le cloisonnement est
     * inactif, si l'appelant est privilégié, ou s'il n'appartient à aucune
     * exploitation — le cas majoritaire, qui retombe alors sur la seule
     * propriété directe.
     */
    public List<Long> visibleFarmIds() {
        if (!isEnabled() || isPrivileged()) {
            return List.of();
        }
        Long userId = currentUserId();
        return userId == null ? List.of() : farmMembershipRepository.findFarmIdsForUser(userId);
    }

    /**
     * Vérifie que l'appelant a le droit de voir cette parcelle.
     *
     * <p>Appelée depuis {@code PlotService.require(id)}, par où passe l'accès à
     * une parcelle dans tous les domaines — cultures, boîtiers, relevés,
     * diagnostics, observations, interventions, récoltes. Un seul contrôle
     * couvre donc l'ensemble.
     */
    public void requireAccess(Plot plot) {
        if (!isEnabled() || isPrivileged() || plot == null) {
            return;
        }

        if (hasAnyAccess(plot)) {
            return;
        }

        // Le message ne dit pas si la parcelle existe : le distinguer
        // permettrait d'énumérer les identifiants d'autrui.
        throw new ForbiddenException("Cette parcelle ne vous est pas accessible.");
    }

    /**
     * Vérifie l'accès à un domaine <em>précis</em> des données de la parcelle.
     *
     * <p>Employée là où le domaine importe — typiquement le bilan économique,
     * qu'un technicien ou un ouvrier n'a pas à consulter. Le propriétaire direct
     * n'est jamais concerné par cette distinction : elle ne s'applique qu'aux
     * membres d'une exploitation, dont le rôle dit ce qu'ils sont venus faire.
     */
    public void requireScope(Plot plot, AccessScope scope) {
        if (!isEnabled() || isPrivileged() || plot == null) {
            return;
        }

        if (isDirectOwner(plot)) {
            return;
        }

        MembershipRole role = roleOn(plot);
        if (role != null && role.allows(scope)) {
            return;
        }

        // Ici le message peut être explicite : l'appelant a déjà prouvé qu'il
        // connaît la parcelle, il n'y a plus rien à lui cacher de son existence.
        // Lui dire pourquoi lui évite de croire à une panne.
        throw new ForbiddenException(String.format(
                "%s : votre rôle sur cette exploitation (%s) ne donne pas accès à ces données.",
                scope.getLabel(),
                role == null ? "aucun" : role.getLabel().toLowerCase(java.util.Locale.FRANCE)));
    }

    /** Variante non levante, pour masquer une section plutôt que refuser l'appel. */
    public boolean canRead(Plot plot, AccessScope scope) {
        if (!isEnabled() || isPrivileged() || plot == null || isDirectOwner(plot)) {
            return true;
        }
        MembershipRole role = roleOn(plot);
        return role != null && role.allows(scope);
    }

    /** Identifiant de l'utilisateur authentifié, ou {@code null}. */
    public Long currentUserId() {
        UserPrincipal principal = currentPrincipal();
        return principal == null || principal.getUser() == null
                ? null
                : principal.getUser().getId();
    }

    public boolean isPrivileged() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PRIVILEGED_ROLES::contains);
    }

    // ============================================================
    // Interne
    // ============================================================

    /** Propriété directe OU appartenance à l'exploitation, dans cet ordre. */
    private boolean hasAnyAccess(Plot plot) {
        return isDirectOwner(plot) || roleOn(plot) != null;
    }

    private boolean isDirectOwner(Plot plot) {
        Long currentUser = currentUserId();
        Long owner = plot.getUser() == null ? null : plot.getUser().getId();
        return currentUser != null && owner != null && currentUser.equals(owner);
    }

    /**
     * Rôle de l'appelant sur l'exploitation de la parcelle, ou {@code null}.
     *
     * <p>Court-circuité quand la parcelle n'a pas d'exploitation : c'est le cas
     * majoritaire, et il ne doit coûter aucune requête. La V22 n'a donc pas
     * dégradé le chemin nominal.
     */
    private MembershipRole roleOn(Plot plot) {
        if (plot.getFarm() == null) {
            return null;
        }
        Long userId = currentUserId();
        if (userId == null) {
            return null;
        }
        return farmMembershipRepository.findRoleForPlot(userId, plot.getId())
                .map(MembershipRole::from)
                .orElse(null);
    }

    private UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
