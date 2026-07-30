package com.sni.bilanga.config;

import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.config.properties.BilangaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Refuse le démarrage lorsque la configuration ne permet pas de servir en
 * confiance.
 *
 * <p><strong>Le problème qu'il résout.</strong> Jusqu'ici, un secret JWT absent
 * de la configuration ne provoquait rien : la valeur codée en dur dans
 * {@code JWTService} prenait le relais. Le service démarrait, signait des jetons
 * avec un secret public, et rien dans les journaux ne le disait. De même, une
 * clé de boîtier absente rendait l'ingestion inopérante sans le moindre message
 * au démarrage — le défaut n'apparaissait qu'au premier 503, côté terrain.
 *
 * <p>Le principe retenu : <strong>en {@code prod}, une configuration douteuse
 * arrête le démarrage ; ailleurs, elle est signalée</strong>. Un service qui
 * refuse de démarrer se remarque tout de suite ; un service qui démarre mal se
 * découvre en production, plus tard, et plus cher.
 *
 * <p>Ce contrôle ne modifie aucun comportement de sécurité : il ne fait que
 * constater. Le durcissement lui-même (permitAll, auto-admin) relève d'un autre
 * chantier et n'est pas engagé ici.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigurationGuard implements InitializingBean {

    /** Longueur minimale d'un secret HMAC-SHA256 digne de ce nom. */
    private static final int MIN_SECRET_LENGTH = 32;

    /**
     * Valeurs qui ne doivent jamais servir en production : le défaut codé en dur
     * de {@code JWTService} et les secrets du profil de développement.
     */
    private static final Set<String> FORBIDDEN_IN_PROD = Set.of(
            "Q7mP2xL9vB4nH6sT1yK8dF5wR3cZ0aEQ7mP2xL9vB4nH6sT1yK8dF5wR3cZ0aE",
            "dev-only-jwt-secret-not-for-production-0123456789abcdef",
            "dev-only-token-hash-secret-not-for-production",
            "dev-device-key-change-me");

    private final Environment environment;
    private final BilangaProperties bilanga;

    /**
     * Les mêmes objets que ceux injectés dans le reste de l'application — et non
     * une relecture par un autre chemin. Valider une valeur que le code n'utilise
     * pas donnerait une garantie fausse.
     */
    private final AppProperties app;

    @Override
    public void afterPropertiesSet() {
        boolean production = environment.matchesProfiles("prod");
        List<String> problems = new ArrayList<>();

        checkSecret(problems, "app.security.jwt.secret", app.getSecurity().getJwt().getSecret(), production);
        checkSecret(problems, "app.security.token-hash.secret", app.getSecurity().getTokenHash().getSecret(), production);
        checkDeviceKey(problems, production);
        checkSecurityPosture(problems, production);
        logHardeningPath();

        if (problems.isEmpty()) {
            log.info("Configuration vérifiée · profil {} · ingestion {} · cloisonnement {} · "
                            + "routes métier {} · CORS {}",
                    String.join(",", environment.getActiveProfiles()),
                    ingestState(),
                    app.getSecurity().getOwnership().isEnabled() ? "actif" : "INACTIF",
                    app.getSecurity().getOpenBusinessRoutes().isEnabled() ? "OUVERTES" : "gardées",
                    String.join(",", app.getSecurity().getCors().getAllowedOriginPatterns()));
            return;
        }

        String report = String.join("\n  · ", problems);

        if (production) {
            // Arrêt net : mieux vaut un service qui ne démarre pas qu'un service
            // qui signe des jetons avec un secret public.
            throw new IllegalStateException(
                    "Configuration inutilisable en production :\n  · " + report);
        }
        log.warn("Configuration incomplète (tolérée hors production) :\n  · {}", report);
    }

    private void checkSecret(List<String> problems, String key, String value, boolean production) {
        if (value == null || value.isBlank()) {
            problems.add(key + " n'est pas défini.");
            return;
        }
        if (production && FORBIDDEN_IN_PROD.contains(value)) {
            problems.add(key + " porte une valeur de développement connue publiquement.");
            return;
        }
        if (value.length() < MIN_SECRET_LENGTH) {
            problems.add(String.format(
                    "%s fait %d caractères ; %d au minimum sont attendus pour HMAC-SHA256.",
                    key, value.length(), MIN_SECRET_LENGTH));
        }
    }

    private void checkDeviceKey(List<String> problems, boolean production) {
        // Authentification des boîtiers levée : la clé n'a plus d'objet, mais
        // l'absence de contrôle mérite d'être dite — et redite en production.
        if (!bilanga.getIngest().isRequireDeviceKey()) {
            if (production) {
                log.error("⚠⚠ bilanga.ingest.require-device-key=false EN PRODUCTION : "
                        + "n'importe quel appelant peut déposer des relevés sur n'importe quelle "
                        + "parcelle. Chaque mesure fabriquée déclenche un diagnostic et peut lever "
                        + "une alerte.");
            } else {
                log.warn("⚠ Authentification des boîtiers levée "
                        + "(bilanga.ingest.require-device-key=false) : l'ingestion accepte "
                        + "n'importe quel appelant. Prévu pour l'intégration IoT.");
            }
            return;
        }

        String key = bilanga.getIngest().getDeviceKey();

        if (key == null || key.isBlank()) {
            problems.add("bilanga.ingest.device-key n'est pas définie : "
                    + "l'ingestion des relevés répondra 503 et la chaîne capteur sera injoignable.");
            return;
        }
        if (production && FORBIDDEN_IN_PROD.contains(key)) {
            problems.add("bilanga.ingest.device-key porte la clé de développement.");
        }
    }

    /**
     * La posture permissive n'est pas corrigée ici — c'est un chantier distinct —
     * mais elle cesse d'être invisible.
     */
    private void checkSecurityPosture(List<String> problems, boolean production) {
        if (!app.getSecurity().getOwnership().isEnabled()) {
            String message = "app.security.ownership.enabled=false : les données ne sont pas "
                    + "cloisonnées par propriétaire, n'importe qui peut consulter n'importe "
                    + "quelle parcelle.";
            if (production) {
                problems.add(message);
            } else {
                log.warn("⚠ {}", message);
            }
        }

        // Le fourre-tout permitAll. Jusqu'ici codé en dur dans SecurityConfig, donc
        // impossible à constater au démarrage : il fallait ouvrir le fichier pour
        // savoir que l'autorisation par URL était court-circuitée.
        if (app.getSecurity().getOpenBusinessRoutes().isEnabled()) {
            String message = "app.security.open-business-routes.enabled=true : toutes les "
                    + "routes métier sont ouvertes sans autorisation. AdminApiAuthorizationManager "
                    + "n'est jamais consulté, et la matrice de permissions de la V24 est inerte "
                    + "— seul @PreAuthorize protège encore les contrôleurs d'administration.";
            if (production) {
                problems.add(message);
            } else {
                log.warn("⚠ {}", message);
            }
        }

        // Une origine générique n'a de sens qu'en développement, où le frontend
        // tourne sur un port arbitraire.
        if (production && app.getSecurity().getCors().getAllowedOriginPatterns().contains("*")) {
            problems.add("app.security.cors.allowed-origin-patterns contient « * » : "
                    + "n'importe quelle page pourrait appeler l'API. Énumérez les origines.");
        }

        if (!app.getSecurity().getAutoAdmin().isEnabled()) {
            return;
        }
        if (production) {
            problems.add("app.security.auto-admin.enabled=true : toute requête sans jeton "
                    + "serait authentifiée comme administrateur.");
        } else {
            log.warn("⚠ Auto-admin actif : une requête SANS JETON est authentifiée comme "
                    + "administrateur. Acceptable en développement, jamais au-delà.");
        }
    }

    /**
     * Ordre de bascule du durcissement, journalisé une fois au démarrage.
     *
     * <p>Il figure ici plutôt que dans un document parce que l'ordre <em>compte</em>
     * et que l'inverser enferme : fermer les routes métier avant qu'un compte
     * administrateur ne fonctionne bloque tout le monde, y compris celui qui
     * voudrait amorcer ce compte. L'avoir sous les yeux au moment où l'on voit les
     * avertissements évite de corriger le mauvais réglage en premier.
     */
    private void logHardeningPath() {
        AppProperties.Security security = app.getSecurity();

        boolean ownershipOff = !security.getOwnership().isEnabled();
        boolean autoAdminOn = security.getAutoAdmin().isEnabled();
        boolean routesOpen = security.getOpenBusinessRoutes().isEnabled();

        if (!ownershipOff && !autoAdminOn && !routesOpen) {
            log.info("Posture de sécurité : durcie (cloisonnement actif, auto-admin coupé, "
                    + "routes métier gardées).");
            return;
        }

        log.warn("""
                Posture de sécurité PERMISSIVE. Ordre de durcissement (impact croissant,
                réversibilité décroissante) — rejouer docs/parcours-fonctionnel.http après
                chaque étape :
                  1. app.security.ownership.enabled            = true   {}
                  2. app.security.auto-admin.enabled           = false  {}
                  3. app.security.open-business-routes.enabled = false  {}
                Prérequis absolu avant l'étape 3 : POST /auth/login rend un jeton, et le
                frontend le transmet sur TOUTES ses requêtes. Sinon plus personne n'entre.""",
                ownershipOff ? "← À FAIRE" : "✓ fait",
                autoAdminOn ? "← à faire" : "✓ fait",
                routesOpen ? "← à faire" : "✓ fait");
    }

    private String ingestState() {
        if (!bilanga.getIngest().isRequireDeviceKey()) {
            return "OUVERTE À TOUS (authentification des boîtiers levée)";
        }
        String key = bilanga.getIngest().getDeviceKey();
        return key != null && !key.isBlank() ? "prête" : "FERMÉE (aucune clé de boîtier)";
    }
}
