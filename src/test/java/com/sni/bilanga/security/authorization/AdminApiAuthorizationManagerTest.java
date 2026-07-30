package com.sni.bilanga.security.authorization;

import com.sni.bilanga.utils.path.ApiPath;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fige la matrice d'autorisation par URL.
 *
 * <p><strong>Pourquoi ce test avant tous les autres.</strong> C'est la classe où
 * une erreur coûte le plus cher, et la seule dont le défaut est parfaitement
 * invisible aujourd'hui : le {@code permitAll} fourre-tout de
 * {@code SecurityConfig} la court-circuite, si bien qu'aucune de ses décisions
 * n'est exercée. Une faute de préfixe y resterait indétectable jusqu'au jour du
 * durcissement — c'est-à-dire jusqu'au moment le plus mal choisi pour la
 * découvrir.
 *
 * <p>Deux propriétés en particulier ne se vérifient pas en relisant le code :
 * l'ordre des tests de préfixe (le cas particulier
 * {@code /plots/{id}/economics} doit précéder le préfixe général
 * {@code /plots}), et le fait qu'une route non cartographiée soit
 * <em>refusée</em> plutôt qu'ouverte.
 */
@DisplayName("AdminApiAuthorizationManager — matrice route → permission")
class AdminApiAuthorizationManagerTest {

    private final AdminApiAuthorizationManager manager = new AdminApiAuthorizationManager();

    // ============================================================
    // Outillage
    // ============================================================

    private boolean granted(String method, String path, String... authorities) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, ApiPath.V1 + path);
        return decide(request, authenticated(authorities));
    }

    private boolean decide(HttpServletRequest request, Supplier<Authentication> authentication) {
        AuthorizationResult result =
                manager.authorize(authentication, new RequestAuthorizationContext(request));
        return result != null && result.isGranted();
    }

    private Supplier<Authentication> authenticated(String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return () -> new UsernamePasswordAuthenticationToken("someone", null, granted);
    }

    // ============================================================
    // Le matériel de terrain n'a pas de jeton
    // ============================================================

    @Nested
    @DisplayName("Ingestion — authentifiée par clé, jamais par jeton")
    class Ingestion {

        /**
         * Le défaut historique : le premier test du gestionnaire était « la
         * requête est-elle authentifiée ? », ce qui écartait tous les boîtiers.
         * Toute l'ingestion de terrain se serait arrêtée le jour du durcissement.
         */
        @ParameterizedTest
        @ValueSource(strings = {"/ingest/readings", "/ingest/readings/batch", "/ingest/health"})
        @DisplayName("passe SANS authentification — le contrôleur vérifie X-Device-Key")
        void ingestPassesUnauthenticated(String path) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", ApiPath.V1 + path);

            assertThat(decide(request, () -> null))
                    .as("un microcontrôleur ne gère pas de cycle de vie de jeton")
                    .isTrue();
        }

        @Test
        @DisplayName("passe aussi avec une authentification anonyme")
        void ingestPassesAnonymous() {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", ApiPath.V1 + "/ingest/readings");

            Supplier<Authentication> anonymous = () -> new AnonymousAuthenticationToken(
                    "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

            assertThat(decide(request, anonymous)).isTrue();
        }
    }

    // ============================================================
    // Absence d'authentification
    // ============================================================

    @Nested
    @DisplayName("Sans authentification")
    class Unauthenticated {

        @Test
        @DisplayName("toute autre route est refusée")
        void nullAuthenticationIsRefused() {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", ApiPath.V1 + "/plots");

            assertThat(decide(request, () -> null)).isFalse();
        }

        @Test
        @DisplayName("une authentification non authentifiée est refusée")
        void notAuthenticatedIsRefused() {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", ApiPath.V1 + "/plots");

            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken("someone", null);
            // Sans autorités, ce jeton se déclare non authentifié.
            assertThat(token.isAuthenticated()).isFalse();

            assertThat(decide(request, () -> token)).isFalse();
        }
    }

    // ============================================================
    // Le super administrateur
    // ============================================================

    @Nested
    @DisplayName("SUPER_ADMIN")
    class SuperAdmin {

        private static final String ROLE = "ROLE_SUPER_ADMIN";

        /**
         * Il passe <em>sans porter</em> la permission. C'est le seul recours si
         * une route venait à manquer dans la table — et la raison pour laquelle
         * le frontend doit tester le rôle en plus du jeu de permissions.
         */
        @ParameterizedTest
        @ValueSource(strings = {
                "/plots", "/plots/42/economics", "/admin/users", "/admin/roles",
                "/knowledge/rules", "/harvests", "/overview/economics",
                "/une/route/qui/nexiste/pas"})
        @DisplayName("passe partout, y compris sur une route non cartographiée")
        void superAdminPassesEverywhere(String path) {
            assertThat(granted("GET", path, ROLE)).isTrue();
            assertThat(granted("DELETE", path, ROLE)).isTrue();
        }
    }

    // ============================================================
    // Routes de service personnel
    // ============================================================

    @Nested
    @DisplayName("Self-service — accessible à tout compte authentifié")
    class SelfService {

        /**
         * Refuser à quelqu'un de lire son propre profil ou de régler ses propres
         * notifications n'aurait aucun sens : ces routes ne donnent accès qu'à
         * ses données.
         */
        @ParameterizedTest
        @ValueSource(strings = {"/auth/me", "/auth/logout", "/notifications/preferences"})
        @DisplayName("passe avec le rôle le plus faible et aucune permission")
        void selfServiceNeedsNoPermission(String path) {
            assertThat(granted("GET", path, "ROLE_EXPLOITANT")).isTrue();
        }

        /**
         * La distinction qui compte : /notifications/preferences est
         * self-service, /admin/notifications ne l'est pas.
         */
        @Test
        @DisplayName("mais /admin/notifications exige SYSTEM:NOTIFICATIONS")
        void adminNotificationsIsNotSelfService() {
            assertThat(granted("GET", "/admin/notifications", "ROLE_EXPLOITANT")).isFalse();
            assertThat(granted("GET", "/admin/notifications",
                    AppPermission.SYSTEM_NOTIFICATIONS.authority())).isTrue();
        }
    }

    // ============================================================
    // La cartographie, route par route
    // ============================================================

    @Nested
    @DisplayName("Cartographie route → permission")
    class Mapping {

        @ParameterizedTest(name = "{0} {1} → {2}")
        @CsvSource({
                // ── Administration système ──────────────────────
                "GET,    /admin/users,                 SYSTEM:USERS",
                "POST,   /admin/users,                 SYSTEM:USERS",
                "DELETE, /admin/users/ABC,             SYSTEM:USERS",
                "POST,   /admin/provisioning/bootstrap-admin, SYSTEM:USERS",
                "GET,    /admin/roles,                 SYSTEM:ROLES",
                "GET,    /admin/permissions,           SYSTEM:PERMISSIONS",
                "GET,    /admin/audit-logs,            SYSTEM:AUDIT",
                "GET,    /admin/settings-audit-logs,   SYSTEM:AUDIT",
                "GET,    /admin/idempotency-records,   SYSTEM:AUDIT",
                "GET,    /admin/notifications,         SYSTEM:NOTIFICATIONS",
                "POST,   /admin/notifications/dispatch, SYSTEM:NOTIFICATIONS",

                // ── Organisation ────────────────────────────────
                "GET,    /admin/cooperatives,          ORGANIZATION:READ",
                "POST,   /admin/cooperatives,          ORGANIZATION:CREATE",
                "PUT,    /admin/farms/7,               ORGANIZATION:UPDATE",
                "DELETE, /admin/farms/7,               ORGANIZATION:DELETE",
                "POST,   /admin/farms/7/members,       ORGANIZATION:CREATE",

                // ── Parcelles et cultures ───────────────────────
                "GET,    /plots,                       FARM:READ",
                "POST,   /plots,                       FARM:CREATE",
                "PUT,    /plots/7,                     FARM:UPDATE",
                "DELETE, /plots/7,                     FARM:DELETE",
                "GET,    /plots/7/timeline,            FARM:READ",
                "GET,    /plots/7/history,             FARM:READ",
                "GET,    /crops,                       FARM:READ",
                "POST,   /crops,                       FARM:CREATE",

                // ── Matériel de terrain ─────────────────────────
                "GET,    /devices,                     IOT:READ",
                "POST,   /devices,                     IOT:CREATE",
                "DELETE, /sensors/9,                   IOT:DELETE",
                "GET,    /readings,                    IOT:READ",
                "POST,   /observations,                IOT:CREATE",

                // ── Diagnostic ──────────────────────────────────
                "GET,    /diagnosis,                   DIAGNOSIS:READ",
                "POST,   /diagnosis/image/predict,     DIAGNOSIS:CREATE",
                "GET,    /diagnosis/7/explain,         DIAGNOSIS:READ",

                // ── Connaissance ────────────────────────────────
                "GET,    /knowledge/crop-requirements, KNOWLEDGE:READ",
                "POST,   /knowledge/diseases,          KNOWLEDGE:CREATE",
                "PUT,    /knowledge/rules/3,           KNOWLEDGE:UPDATE",
                "DELETE, /knowledge/correlations/3,    KNOWLEDGE:DELETE",

                // ── Journal des actions ─────────────────────────
                "GET,    /interventions,               INTERVENTION:READ",
                "POST,   /interventions,               INTERVENTION:CREATE",
                "GET,    /interventions/5/effect,      INTERVENTION:READ",
                "DELETE, /interventions/5,             INTERVENTION:DELETE",
                "GET,    /harvests,                    HARVEST:READ",
                "POST,   /harvests,                    HARVEST:CREATE",

                // ── Tableaux de bord ────────────────────────────
                "GET,    /overview/plots,              OVERVIEW:READ",
                "GET,    /overview/farm,               OVERVIEW:READ",
        })
        @DisplayName("la permission attendue ouvre, une autre ferme")
        void routeRequiresExactPermission(String method, String path, String permission) {
            assertThat(granted(method, path, permission))
                    .as("%s %s devrait exiger %s", method, path, permission)
                    .isTrue();

            // Une permission d'un autre module ne doit pas ouvrir la route :
            // sans ce second volet, un test qui accorde toutes les permissions
            // passerait quelle que soit la cartographie.
            String foreign = permission.startsWith("FARM")
                    ? AppPermission.IOT_READ.authority()
                    : AppPermission.FARM_READ.authority();

            assertThat(granted(method, path, foreign))
                    .as("%s %s ne devrait pas s'ouvrir avec %s", method, path, foreign)
                    .isFalse();
        }
    }

    // ============================================================
    // Les trois pièges de la cartographie
    // ============================================================

    @Nested
    @DisplayName("Les cas particuliers, qui doivent précéder les préfixes généraux")
    class SpecialCases {

        /**
         * Le piège n°1, et celui qui compte le plus. Le bilan économique expose
         * des marges et des prix de vente : le rôle chargé du parc matériel ou du
         * suivi agronomique n'a pas à les consulter. Si la règle
         * {@code /plots/{id}/economics} passait APRÈS le préfixe {@code /plots},
         * elle serait morte et {@code FARM:READ} suffirait à lire la
         * comptabilité.
         */
        @Test
        @DisplayName("/plots/{id}/economics relève de HARVEST:READ, pas de FARM:READ")
        void plotEconomicsIsHarvestNotFarm() {
            assertThat(granted("GET", "/plots/42/economics",
                    AppPermission.HARVEST_READ.authority())).isTrue();

            assertThat(granted("GET", "/plots/42/economics",
                    AppPermission.FARM_READ.authority()))
                    .as("FARM:READ ouvrirait la comptabilité à qui suit l'agronomie")
                    .isFalse();
        }

        @Test
        @DisplayName("/overview/economics aussi, et non OVERVIEW:READ")
        void overviewEconomicsIsHarvest() {
            assertThat(granted("GET", "/overview/economics",
                    AppPermission.HARVEST_READ.authority())).isTrue();

            assertThat(granted("GET", "/overview/economics",
                    AppPermission.OVERVIEW_READ.authority())).isFalse();
        }

        /**
         * Le piège n°2 : acquitter une alerte est un {@code PATCH}, donc un
         * {@code UPDATE}. Le classer en {@code CREATE} exigerait le droit de
         * lancer un diagnostic pour refermer une alerte.
         */
        @ParameterizedTest(name = "PATCH /alerts/1/{0} → DIAGNOSIS:UPDATE")
        @ValueSource(strings = {"acknowledge", "resolve", "assign"})
        @DisplayName("le cycle de vie d'une alerte est un UPDATE")
        void alertLifecycleIsUpdate(String action) {
            String path = "/alerts/1/" + action;

            assertThat(granted("PATCH", path, AppPermission.DIAGNOSIS_UPDATE.authority())).isTrue();
            assertThat(granted("PATCH", path, AppPermission.DIAGNOSIS_READ.authority())).isFalse();
            assertThat(granted("PATCH", path, AppPermission.DIAGNOSIS_CREATE.authority()))
                    .as("acquitter ne crée rien")
                    .isFalse();
        }

        @Test
        @DisplayName("lire les alertes ou les conseils relève de DIAGNOSIS:READ")
        void readingAlertsIsDiagnosisRead() {
            assertThat(granted("GET", "/alerts", AppPermission.DIAGNOSIS_READ.authority())).isTrue();
            assertThat(granted("GET", "/recommendations",
                    AppPermission.DIAGNOSIS_READ.authority())).isTrue();
            assertThat(granted("PATCH", "/recommendations/1/feedback",
                    AppPermission.DIAGNOSIS_UPDATE.authority())).isTrue();
        }

        /**
         * Le piège n°3 : {@code PUT} et {@code PATCH} relèvent tous deux
         * d'{@code UPDATE}. Les séparer obligerait à doubler chaque permission de
         * modification, sans qu'aucun rôle n'ait besoin de la distinction.
         */
        /**
         * Le piège n°4, découvert <em>par ce test</em>.
         * {@code RoleAssignmentAdminController} est à cheval sur deux préfixes, et
         * ses {@code @PreAuthorize} ne suivent pas celui du chemin. La matrice par
         * URL était donc plus stricte que le contrat documenté : un compte porteur
         * de {@code SYSTEM:PERMISSIONS} mais non de {@code SYSTEM:ROLES} se voyait
         * refuser l'écran d'attribution des permissions, avant même que
         * {@code @PreAuthorize} — qui l'aurait accepté — ne soit consulté.
         */
        @Test
        @DisplayName("attribuer des permissions à un rôle relève de SYSTEM:PERMISSIONS")
        void rolePermissionAssignmentIsPermissionsNotRoles() {
            for (String path : List.of("/admin/roles/ADMIN/permissions",
                    "/admin/roles/ADMIN/permission-names")) {

                assertThat(granted("PATCH", path, AppPermission.SYSTEM_PERMISSIONS.authority()))
                        .as("%s : le contrôleur exige SYSTEM:PERMISSIONS", path)
                        .isTrue();
                assertThat(granted("GET", path, AppPermission.SYSTEM_PERMISSIONS.authority()))
                        .isTrue();
            }
        }

        @Test
        @DisplayName("attribuer un rôle à un utilisateur relève de SYSTEM:ROLES")
        void userRoleAssignmentIsRolesNotUsers() {
            assertThat(granted("POST", "/admin/users/42/roles",
                    AppPermission.SYSTEM_ROLES.authority()))
                    .as("le contrôleur exige SYSTEM:ROLES malgré le préfixe /admin/users")
                    .isTrue();

            assertThat(granted("DELETE", "/admin/users/42/roles/7",
                    AppPermission.SYSTEM_ROLES.authority())).isTrue();
        }

        @Test
        @DisplayName("mais /admin/roles sans sous-ressource reste SYSTEM:ROLES")
        void bareRolesStaysRoles() {
            assertThat(granted("GET", "/admin/roles", AppPermission.SYSTEM_ROLES.authority()))
                    .isTrue();
            assertThat(granted("PUT", "/admin/roles/ADMIN", AppPermission.SYSTEM_ROLES.authority()))
                    .isTrue();
            assertThat(granted("PUT", "/admin/roles/ADMIN",
                    AppPermission.SYSTEM_PERMISSIONS.authority())).isFalse();
        }

        @Test
        @DisplayName("PUT et PATCH exigent la même permission")
        void putAndPatchShareUpdate() {
            String permission = AppPermission.FARM_UPDATE.authority();

            assertThat(granted("PUT", "/plots/7", permission)).isTrue();
            assertThat(granted("PATCH", "/plots/7", permission)).isTrue();
        }

        @Test
        @DisplayName("HEAD est traité comme GET")
        void headBehavesLikeGet() {
            assertThat(granted("HEAD", "/plots", AppPermission.FARM_READ.authority())).isTrue();
        }
    }

    // ============================================================
    // Refus par défaut
    // ============================================================

    @Nested
    @DisplayName("Route non cartographiée")
    class Unmapped {

        /**
         * <strong>Refusée, pas ouverte.</strong> L'inverse ferait qu'un contrôleur
         * nouvellement ajouté serait accessible à tout compte authentifié, sans
         * que rien ne le signale. Ici l'oubli produit un 403 bruyant.
         */
        @ParameterizedTest
        @ValueSource(strings = {
                "/une/route/inventee",
                "/payments/mobile-money/callback",
                "/documents/7/preview"})
        @DisplayName("est refusée, même à un compte largement pourvu")
        void unmappedIsRefusedByDefault(String path) {
            assertThat(granted("GET", path,
                    AppPermission.FARM_READ.authority(),
                    AppPermission.IOT_READ.authority(),
                    AppPermission.DIAGNOSIS_READ.authority(),
                    AppPermission.OVERVIEW_READ.authority(),
                    "ROLE_EXPLOITANT"))
                    .as("un oubli de cartographie doit se voir, pas s'ouvrir")
                    .isFalse();
        }

        @Test
        @DisplayName("sauf laissez-passer explicite ADMIN:ACCESS")
        void adminAccessIsTheEscapeHatch() {
            assertThat(granted("GET", "/une/route/inventee",
                    AppPermission.ADMIN_ACCESS.authority())).isTrue();
        }

        /**
         * Toute route {@code /admin/} non nommée retombe sur
         * {@code SYSTEM:SETTINGS} plutôt que sur le refus par défaut : le préfixe
         * suffit à dire qu'il s'agit d'administration.
         */
        @Test
        @DisplayName("une route /admin/ inconnue retombe sur SYSTEM:SETTINGS")
        void unknownAdminFallsBackToSettings() {
            assertThat(granted("GET", "/admin/quelque-chose-de-neuf",
                    AppPermission.SYSTEM_SETTINGS.authority())).isTrue();

            assertThat(granted("GET", "/admin/quelque-chose-de-neuf",
                    AppPermission.SYSTEM_USERS.authority())).isFalse();
        }
    }

    // ============================================================
    // Normalisation du chemin
    // ============================================================

    @Nested
    @DisplayName("Normalisation")
    class Normalization {

        @Test
        @DisplayName("la casse du chemin est indifférente")
        void pathIsCaseInsensitive() {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", ApiPath.V1 + "/PLOTS");

            assertThat(decide(request, authenticated(AppPermission.FARM_READ.authority()))).isTrue();
        }

        /**
         * Le préfixe d'API est retiré avant la cartographie. Un chemin qui n'en
         * porterait pas — cas d'un reverse-proxy mal configuré qui l'aurait
         * absorbé — doit rester cartographié, sinon toutes les routes basculeraient
         * d'un coup sur le refus par défaut.
         */
        @Test
        @DisplayName("un chemin sans le préfixe /sni/api/v1 reste cartographié")
        void pathWithoutPrefixStillMaps() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/plots");

            assertThat(decide(request, authenticated(AppPermission.FARM_READ.authority()))).isTrue();
        }
    }

    // ============================================================
    // Cohérence avec les rôles semés par la V24
    // ============================================================

    @Nested
    @DisplayName("Cohérence des rôles de SecurityRole")
    class RoleConsistency {

        /**
         * Un exploitant doit pouvoir consulter ses parcelles. Le gestionnaire
         * exigeait auparavant {@code ADMIN} ou {@code SUPER_ADMIN} avant même de
         * regarder la permission : l'utilisateur principal du produit se voyait
         * refuser toutes les routes, y compris celles de ses propres parcelles.
         */
        @Test
        @DisplayName("un EXPLOITANT accède à ses parcelles")
        void exploitantReachesPlots() {
            String[] authorities = withRole(SecurityRole.EXPLOITANT);

            assertThat(granted("GET", "/plots", authorities)).isTrue();
            assertThat(granted("POST", "/plots", authorities)).isTrue();
            assertThat(granted("GET", "/overview/farm", authorities)).isTrue();
        }

        @Test
        @DisplayName("un TECHNICIEN gère le parc mais n'atteint ni diagnostic ni économie")
        void technicienIsLimitedToHardware() {
            String[] authorities = withRole(SecurityRole.TECHNICIEN);

            assertThat(granted("GET", "/devices", authorities)).isTrue();
            assertThat(granted("POST", "/devices", authorities)).isTrue();
            assertThat(granted("GET", "/plots", authorities))
                    .as("il doit voir la parcelle où intervenir")
                    .isTrue();

            assertThat(granted("GET", "/diagnosis", authorities))
                    .as("réparer une sonde ne demande pas de savoir ce qu'elle mesure")
                    .isFalse();
            assertThat(granted("GET", "/plots/1/economics", authorities))
                    .as("encore moins ce que la parcelle rapporte")
                    .isFalse();
            assertThat(granted("GET", "/harvests", authorities)).isFalse();
        }

        @Test
        @DisplayName("un AGRONOME pilote la connaissance mais n'a aucun droit système")
        void agronomeOwnsKnowledgeNotSystem() {
            String[] authorities = withRole(SecurityRole.AGRONOME);

            assertThat(granted("PUT", "/knowledge/rules/1", authorities)).isTrue();
            assertThat(granted("DELETE", "/knowledge/rules/1", authorities)).isTrue();

            assertThat(granted("GET", "/admin/users", authorities)).isFalse();
            assertThat(granted("GET", "/admin/roles", authorities)).isFalse();
            assertThat(granted("DELETE", "/plots/1", authorities))
                    .as("l'expert des seuils n'a pas à disposer du patrimoine foncier")
                    .isFalse();
        }

        @Test
        @DisplayName("un EXPLOITANT lit la connaissance sans pouvoir la modifier")
        void exploitantReadsKnowledgeOnly() {
            String[] authorities = withRole(SecurityRole.EXPLOITANT);

            assertThat(granted("GET", "/knowledge/crop-requirements", authorities)).isTrue();
            assertThat(granted("PUT", "/knowledge/crop-requirements/1", authorities))
                    .as("un seuil engage toutes les exploitations, pas seulement la sienne")
                    .isFalse();
        }

        /** Autorités telles que {@code UserPrincipal} les compose : rôle + permissions. */
        private String[] withRole(SecurityRole role) {
            List<String> authorities = new java.util.ArrayList<>();
            authorities.add(role.authority());
            authorities.addAll(role.permissionAuthorities());
            return authorities.toArray(String[]::new);
        }
    }
}
