package com.sni.bilanga.security.admin.provisioning;

import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.security.admin.provisioning.service.interfaces.UserProvisioningService;
import com.sni.bilanga.security.admin.role.service.interfaces.RoleUserService;
import com.sni.bilanga.security.admin.user.dto.request.UserRequest;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.authorization.SecurityRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Crée le compte administrateur de développement au démarrage, s'il n'existe pas.
 *
 * <h2>Le problème résolu</h2>
 *
 * <p>Après la migration V24, la base porte 36 permissions, 5 rôles et leurs 116
 * liaisons — mais <strong>aucun utilisateur</strong>. Personne ne peut donc se
 * connecter, et la seule porte d'entrée était un appel manuel à
 * {@code POST /admin/provisioning/bootstrap-admin}, à refaire après chaque base
 * neuve.
 *
 * <p>Son oubli produit un symptôme particulièrement trompeur : l'auto-admin cherche
 * {@code admin@bilanga.cg}, ne le trouve pas, échoue <strong>silencieusement</strong>,
 * et {@code /admin/**} répond 403. Rien dans le message ne dit qu'il manque
 * simplement un compte.
 *
 * <h2>Pourquoi un composant et non une migration Flyway</h2>
 *
 * <p><strong>Une migration s'applique partout, y compris en production</strong>, et
 * le hachage du mot de passe resterait dans le dépôt pour toujours — lisible par
 * quiconque a accès au code, et impossible à retirer de l'historique Git. Un compte
 * administrateur au mot de passe public, dans un système qui gère des exploitations
 * agricoles et leurs données économiques, n'est pas une commodité : c'est une porte
 * ouverte.
 *
 * <p>D'où {@link Profile @Profile("dev")} : <strong>ce bean n'existe pas ailleurs
 * qu'en développement</strong>. Ce n'est pas un réglage qu'on peut activer par
 * mégarde en production — il n'y a pas de code pour le faire. Le contrôle
 * défensif de {@link #run} n'est qu'une ceinture par-dessus les bretelles, au cas où
 * quelqu'un élargirait l'annotation sans mesurer ce qu'il fait.
 *
 * <h2>Trois propriétés qui le rendent sûr</h2>
 *
 * <p><strong>1. Idempotent.</strong> Si un compte porte déjà le rôle {@code ADMIN},
 * il ne fait rien. Redémarrer dix fois ne crée pas dix comptes — et surtout, ne
 * <em>réinitialise pas</em> un mot de passe que vous auriez changé. C'est la
 * différence entre un amorçage et un écrasement.
 *
 * <p><strong>2. Il ne peut pas empêcher le démarrage.</strong> Tout échec est
 * journalisé et avalé. Une base momentanément incohérente — migration à moitié
 * appliquée, table verrouillée — ne doit pas rendre l'application impossible à
 * lancer, précisément au moment où l'on cherche à la réparer.
 *
 * <p><strong>3. Il dit ce qu'il a fait, en clair.</strong> Le mot de passe est
 * journalisé. C'est délibéré et cela n'aurait aucun sens ailleurs : l'objet même de
 * ce composant est que vous puissiez vous connecter, et un identifiant qu'on ne
 * connaît pas ne sert à rien.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
// Après Flyway et après ConfigurationGuard : les rôles semés par la V24 doivent
// exister avant qu'on tente d'en attribuer un.
@Order(100)
public class DefaultAdminSeeder implements ApplicationRunner {

    private final UserProvisioningService provisioningService;
    private final RoleUserService roleUserService;
    private final AppProperties app;
    private final org.springframework.core.env.Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Security.BootstrapAdmin config = app.getSecurity().getBootstrapAdmin();

        if (!config.isEnabled()) {
            log.info("Amorçage du compte administrateur désactivé "
                    + "(app.security.bootstrap-admin.enabled=false).");
            return;
        }

        // Ceinture par-dessus les bretelles : @Profile("dev") suffit déjà à ce que ce
        // bean n'existe pas en production. Ce contrôle protège du jour où quelqu'un
        // élargirait l'annotation sans mesurer ce qu'il ouvre.
        if (environment.matchesProfiles("prod")) {
            log.error("⚠⚠ DefaultAdminSeeder atteint en profil prod : amorçage REFUSÉ. "
                    + "Ce composant ne doit exister qu'en développement — vérifiez son @Profile.");
            return;
        }

        try {
            seed(config);
        } catch (Exception e) {
            // Un amorçage raté ne doit jamais coûter le démarrage : c'est un confort de
            // développement, pas une dépendance de l'application.
            log.warn("Amorçage du compte administrateur ignoré : {}. "
                    + "Vous pouvez toujours passer par "
                    + "POST /sni/api/v1/admin/provisioning/bootstrap-admin.", e.getMessage());
        }
    }

    private void seed(AppProperties.Security.BootstrapAdmin config) {
        // La même question que celle posée par initializeGlobalAdmin, posée avant :
        // elle évite de provoquer un ConflictException au démarrage pour le cas
        // parfaitement normal d'une base déjà amorcée.
        if (roleUserService.hasAnyUserAssignedToRole(SecurityRole.ADMIN.name())) {
            log.info("Un administrateur existe déjà : amorçage inutile. "
                    + "Le mot de passe en configuration n'est PAS réappliqué.");
            warnIfAutoAdminMismatched(config);
            return;
        }

        UserRequest request = new UserRequest();
        request.setEmail(config.getEmail());
        request.setFirstname(config.getFirstname());
        request.setLastname(config.getLastname());
        request.setPassword(config.getPassword());
        request.setGeneratePassword(false);

        Users admin = provisioningService.initializeGlobalAdmin(request);

        // Journalisé en clair, et c'est tout l'objet du composant : un identifiant
        // qu'on ne connaît pas ne sert à rien. Le bean n'existe qu'en développement.
        log.warn("""

                ════════════════════════════════════════════════════════════
                 COMPTE ADMINISTRATEUR DE DÉVELOPPEMENT CRÉÉ
                ════════════════════════════════════════════════════════════
                   adresse       : {}
                   mot de passe  : {}
                   rôle          : ADMIN (36 permissions)

                   POST /sni/api/v1/auth/login
                   {{ "email": "{}", "password": "{}" }}

                 ⚠ Identifiants de DÉVELOPPEMENT, connus et publics.
                   Ce compte n'est jamais créé en production : le composant
                   qui l'amorce porte @Profile("dev").
                ════════════════════════════════════════════════════════════""",
                admin.getEmail(), config.getPassword(),
                admin.getEmail(), config.getPassword());

        warnIfAutoAdminMismatched(config);
    }

    /**
     * Signale l'écart le plus coûteux à diagnostiquer.
     *
     * <p>Si l'adresse amorcée diffère de celle que cherche l'auto-admin, ce dernier
     * ne trouve pas le compte, échoue <strong>silencieusement</strong>, et
     * {@code /admin/**} répond 403 — sans que rien n'en donne la raison. Le dire une
     * fois au démarrage économise une heure de recherche.
     */
    private void warnIfAutoAdminMismatched(AppProperties.Security.BootstrapAdmin config) {
        String autoAdminEmail = app.getSecurity().getAutoAdmin().getEmail();

        if (!app.getSecurity().getAutoAdmin().isEnabled()
                || autoAdminEmail == null
                || autoAdminEmail.equalsIgnoreCase(config.getEmail())) {
            return;
        }

        log.warn("⚠ app.security.bootstrap-admin.email ({}) diffère de "
                + "app.security.auto-admin.email ({}) : les requêtes SANS JETON ne seront "
                + "PAS authentifiées, et /admin/** répondra 403 sans autre explication. "
                + "Alignez les deux, ou connectez-vous vraiment.",
                config.getEmail(), autoAdminEmail);
    }
}
