package com.sni.bilanga.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages transverses et de sécurité.
 *
 * <p>Pendant de {@link BilangaProperties} pour le préfixe {@code app}. Ces vingt-cinq
 * clés étaient lues par autant d'annotations {@code @Value} réparties dans dix
 * classes, chacune redéclarant sa valeur par défaut — dont, à trois endroits,
 * la durée de vie du jeton de rafraîchissement, avec le risque que deux copies
 * finissent par diverger.
 *
 * <p><strong>Aucun défaut sensible ici.</strong> Le secret JWT avait pour valeur
 * de repli une chaîne codée en dur : la configuration pouvait rester muette sans
 * que rien ne l'indique, et le service signait des jetons avec un secret public.
 * Les secrets valent désormais la chaîne vide par défaut, et
 * {@link com.sni.bilanga.config.ConfigurationGuard} refuse le démarrage en
 * production dans ce cas.
 *
 * <p>Cette classe ne change aucun comportement de sécurité : elle change la
 * façon dont les valeurs sont lues, pas ce qu'elles déclenchent.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Ajoute les champs de diagnostic aux réponses d'erreur. Jamais en production. */
    private boolean devMode = false;

    /** Fuseau de référence pour l'affichage des horodatages. */
    private String timeZone = "Africa/Lagos";

    /** Base de l'URL de la console d'administration, utilisée dans les courriels. */
    private String adminAccessUrl = "http://localhost:3000";

    @Valid private final Error error = new Error();
    @Valid private final Security security = new Security();

    @Data
    public static class Error {
        private boolean verbose = false;
    }

    // Le bloc « documents » (app.documents.public-preview-enabled) a été retiré au
    // lot 5 avec le reste du scaffolding : il pilotait un aperçu d'images pour un
    // module /documents qui n'existe pas dans ce projet. Son seul lecteur était
    // JWTFilter, qui déclarait publique une route absente.

    @Data
    public static class Security {

        @Valid private final Jwt jwt = new Jwt();
        @Valid private final TokenHash tokenHash = new TokenHash();
        @Valid private final OneTimeToken oneTimeToken = new OneTimeToken();
        @Valid private final PasswordReset passwordReset = new PasswordReset();
        @Valid private final FailedLogin failedLogin = new FailedLogin();
        @Valid private final AutoAdmin autoAdmin = new AutoAdmin();
        @Valid private final RateLimit rateLimit = new RateLimit();
        @Valid private final Ownership ownership = new Ownership();
        @Valid private final OpenBusinessRoutes openBusinessRoutes = new OpenBusinessRoutes();
        @Valid private final Cors cors = new Cors();
        @Valid private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

        /**
         * Compte administrateur créé automatiquement au démarrage, en développement.
         *
         * <h2>Le problème résolu</h2>
         *
         * <p>Après la V24, la base contient 36 permissions, 5 rôles et leurs 116
         * liaisons — mais <strong>aucun utilisateur</strong>. Personne ne peut donc se
         * connecter, et la seule porte d'entrée est un appel manuel à
         * {@code POST /admin/provisioning/bootstrap-admin}. C'est une étape à refaire
         * après chaque base neuve, et son oubli produit un symptôme trompeur :
         * l'auto-admin ne trouve pas son compte, échoue <em>silencieusement</em>, et
         * {@code /admin/**} répond 403 sans qu'on sache pourquoi.
         *
         * <h2>Pourquoi ici et pas dans une migration Flyway</h2>
         *
         * <p><strong>Une migration s'applique partout, y compris en production</strong>,
         * et le hachage du mot de passe resterait dans le dépôt pour toujours — lisible
         * par quiconque a accès au code. Un compte administrateur au mot de passe
         * public dans un système qui gère des exploitations agricoles n'est pas une
         * commodité, c'est une porte ouverte.
         *
         * <p>L'amorçage est donc porté par un composant <strong>annoté
         * {@code @Profile("dev")}</strong> : le bean n'existe tout simplement pas
         * ailleurs. Même en forçant {@code enabled=true} en production, rien ne se
         * produirait — il n'y a pas de code pour le faire.
         *
         * <h2>Trois propriétés à connaître</h2>
         *
         * <p><strong>Idempotent.</strong> Si un compte porte déjà le rôle
         * {@code ADMIN}, l'amorçage ne fait rien. Redémarrer dix fois ne crée pas dix
         * comptes, et ne réinitialise pas un mot de passe que vous auriez changé.
         *
         * <p><strong>Ne peut pas empêcher le démarrage.</strong> Tout échec est
         * journalisé et avalé : une base momentanément incohérente ne doit pas rendre
         * l'application impossible à lancer, précisément quand on cherche à la réparer.
         *
         * <p><strong>L'adresse par défaut est celle de l'auto-admin.</strong> C'est ce
         * qui fait que vos requêtes sans jeton sont authentifiées comme cet
         * administrateur. En changer sans changer {@code auto-admin.email} rétablit le
         * symptôme décrit plus haut.
         */
        @Data
        public static class BootstrapAdmin {

            private boolean enabled = true;

            /**
             * Doit correspondre à {@code auto-admin.email}, sans quoi l'auto-admin ne
             * retrouve pas le compte et {@code /admin/**} répond 403.
             */
            private String email = "admin@bilanga.cg";

            /**
             * Mot de passe en clair, encodé par {@code createUser} avant stockage.
             *
             * <p>Volontairement <strong>reconnaissable</strong> : il doit sauter aux
             * yeux dans une capture d'écran ou un journal, pour que personne ne
             * l'emporte par inadvertance ailleurs qu'en développement.
             */
            private String password = "Bilanga@Dev2026";

            private String firstname = "Admin";
            private String lastname = "Bilanga";
        }

        /**
         * Le {@code permitAll} fourre-tout sur {@code /sni/api/v1/**}.
         *
         * <p><strong>Ce que c'était.</strong> Une ligne
         * {@code ApiPath.V1 + "/**"} figurait en dur dans la liste des chemins
         * publics de {@code SecurityConfig}, <em>avant</em> la règle qui délègue à
         * {@code AdminApiAuthorizationManager}. Spring Security évalue dans
         * l'ordre de déclaration : la première correspondance gagne, et toutes
         * les routes métier étaient donc ouvertes. Le gestionnaire d'autorisation
         * n'était jamais consulté, la matrice de permissions semée par la V24
         * était écrite et <strong>inerte</strong>, et seul {@code @PreAuthorize}
         * protégeait encore quelque chose.
         *
         * <p><strong>Pourquoi une clé et non une suppression.</strong> Retirer la
         * ligne du code ferme les routes métier à l'instant où l'on recompile. Or
         * cela n'est tenable qu'une fois un compte administrateur amorcé et le
         * frontend émettant son jeton — sans quoi plus personne n'entre, y compris
         * pour amorcer le compte. Une clé de configuration rend le durcissement
         * <em>réversible et daté</em> : on le bascule quand les prérequis sont
         * réunis, pas quand le code est prêt.
         *
         * <p><strong>Ordre de bascule</strong> (impact croissant, réversibilité
         * décroissante) : {@code ownership.enabled=true}, puis
         * {@code auto-admin.enabled=false}, puis cette clé à {@code false}.
         * Prérequis avant celle-ci : {@code POST /auth/login} rend un jeton, et le
         * frontend le transmet sur toutes ses requêtes.
         */
        @Data
        public static class OpenBusinessRoutes {

            /**
             * {@code true} — état historique : les routes métier répondent sans
             * autorisation. {@code false} — chaque route est confrontée à la
             * matrice de {@code AdminApiAuthorizationManager}.
             */
            private boolean enabled = true;
        }

        /**
         * Origines autorisées en partage de ressources entre origines.
         *
         * <p>Le motif {@code "*"} était codé en dur. Il est acceptable en
         * développement — le frontend tourne sur un port arbitraire — mais laisse
         * n'importe quelle page appeler l'API dès que celle-ci est joignable. La
         * liste est donc déclarée par profil : permissive en {@code dev},
         * énumérée ailleurs.
         *
         * <p>{@code allowCredentials} reste à {@code false} : l'authentification
         * passe par un en-tête {@code Authorization}, jamais par un cookie. Le
         * navigateur n'a donc rien à envoyer d'implicite, et le motif {@code "*"}
         * ne peut pas être détourné pour rejouer une session — ce qui serait le
         * cas avec des cookies.
         */
        @Data
        public static class Cors {

            /**
             * Motifs d'origine, au sens de
             * {@code CorsConfiguration.setAllowedOriginPatterns}. Les caractères
             * génériques sont acceptés : {@code https://*.bilanga.cg}.
             */
            private java.util.List<String> allowedOriginPatterns =
                    new java.util.ArrayList<>(java.util.List.of("*"));
        }

        /**
         * Cloisonnement des données par propriétaire.
         *
         * <p>À {@code false}, {@code ?userId=} est honoré tel quel et n'importe
         * qui peut consulter n'importe quelle parcelle — l'état historique du
         * projet. À {@code true}, un utilisateur non privilégié ne voit que ses
         * propres parcelles, quel que soit le paramètre transmis.
         *
         * <p><strong>Désactivé par défaut, et ce n'est pas un oubli</strong> : les
         * tables de sécurité sont vides (aucun utilisateur, aucun rôle). Activer
         * le cloisonnement avant qu'un compte n'existe rendrait l'API inutilisable,
         * puisque plus aucune requête ne serait rattachée à un propriétaire.
         * À basculer dès que le socle de comptes est amorcé.
         */
        @Data
        public static class Ownership {
            private boolean enabled = false;
        }

        @Data
        public static class Jwt {
            /** Vide par défaut : voir le commentaire de classe. */
            private String secret = "";

            @Min(1) private long accessTokenExpirationMs = 90_000_000L;
            @Min(1) private long refreshTokenExpirationMs = 604_800_000L;
            @Min(1) private long verificationTokenExpirationMs = 900_000L;
        }

        @Data
        public static class TokenHash {
            /** Sert à hacher les jetons de rafraîchissement avant stockage. */
            private String secret = "";
        }

        @Data
        public static class OneTimeToken {
            @Min(1) private long expirationMs = 900_000L;
        }

        @Data
        public static class PasswordReset {
            @Min(1) private long expirationMs = 900_000L;
        }

        @Data
        public static class FailedLogin {
            @Min(1) private int maxAttempts = 5;
        }

        /**
         * Authentifie toute requête dépourvue de jeton comme un administrateur.
         *
         * Posture de développement, refusée en production par
         * {@code ConfigurationGuard}. Son durcissement relève d'un autre chantier :
         * la valeur par défaut est inchangée ici.
         */
        @Data
        public static class AutoAdmin {
            private boolean enabled = true;
            private String email = "admin@bilanga.cg";
        }

        @Data
        public static class RateLimit {

            private boolean enabled = true;

            @Valid private final Window login = new Window(5, 60);
            @Valid private final Window otp = new Window(3, 600);
            @Valid private final Window callback = new Window(120, 60);
            @Valid private final Window admin = new Window(600, 60);

            /** Fenêtre fixe : au plus {@code maxRequests} sur {@code windowSeconds}. */
            @Data
            public static class Window {

                @Min(1) private int maxRequests;
                @Min(1) private long windowSeconds;

                public Window() {
                }

                public Window(int maxRequests, long windowSeconds) {
                    this.maxRequests = maxRequests;
                    this.windowSeconds = windowSeconds;
                }
            }
        }
    }
}
