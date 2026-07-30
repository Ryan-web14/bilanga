package com.sni.bilanga.security.admin.provisioning;

import com.sni.bilanga.config.properties.AppProperties;
import com.sni.bilanga.security.admin.provisioning.service.interfaces.UserProvisioningService;
import com.sni.bilanga.security.admin.role.service.interfaces.RoleUserService;
import com.sni.bilanga.security.admin.user.dto.request.UserRequest;
import com.sni.bilanga.security.admin.user.model.Users;
import com.sni.bilanga.security.authorization.SecurityRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Les trois garanties de l'amorçage, et le refus en production.
 *
 * <p>Aucune ne se vérifie en relisant le code : elles portent sur ce que le
 * composant <em>ne fait pas</em> — ne pas réécraser un mot de passe changé, ne pas
 * empêcher le démarrage, ne rien créer hors développement. Ce sont exactement les
 * propriétés qu'une « simplification » future casserait sans bruit.
 */
@DisplayName("DefaultAdminSeeder — amorçage de développement")
class DefaultAdminSeederTest {

    private UserProvisioningService provisioningService;
    private RoleUserService roleUserService;
    private AppProperties app;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        provisioningService = Mockito.mock(UserProvisioningService.class);
        roleUserService = Mockito.mock(RoleUserService.class);
        app = new AppProperties();
        environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        Users created = new Users();
        created.setEmail(app.getSecurity().getBootstrapAdmin().getEmail());
        Mockito.when(provisioningService.initializeGlobalAdmin(any())).thenReturn(created);
    }

    private DefaultAdminSeeder seeder() {
        return new DefaultAdminSeeder(provisioningService, roleUserService, app, environment);
    }

    private void run() {
        seeder().run(null);
    }

    // ============================================================
    // Le cas nominal
    // ============================================================

    @Nested
    @DisplayName("Base vierge")
    class FreshDatabase {

        @Test
        @DisplayName("crée l'administrateur avec les identifiants configurés")
        void createsAdminFromConfiguration() {
            Mockito.when(roleUserService.hasAnyUserAssignedToRole(anyString())).thenReturn(false);

            run();

            ArgumentCaptor<UserRequest> captor = ArgumentCaptor.forClass(UserRequest.class);
            Mockito.verify(provisioningService).initializeGlobalAdmin(captor.capture());

            UserRequest request = captor.getValue();
            assertThat(request.getEmail()).isEqualTo("admin@bilanga.cg");
            assertThat(request.getPassword()).isEqualTo("Bilanga@Dev2026");
            assertThat(request.isGeneratePassword())
                    .as("un mot de passe engendré serait imprévisible : "
                            + "tout l'objet du composant est qu'on le connaisse")
                    .isFalse();
        }

        @Test
        @DisplayName("l'existence d'un administrateur est bien interrogée sur le rôle ADMIN")
        void checksTheAdminRole() {
            Mockito.when(roleUserService.hasAnyUserAssignedToRole(anyString())).thenReturn(false);

            run();

            Mockito.verify(roleUserService).hasAnyUserAssignedToRole(SecurityRole.ADMIN.name());
        }

        @Test
        @DisplayName("les identifiants configurés priment sur les défauts")
        void configurationOverridesDefaults() {
            app.getSecurity().getBootstrapAdmin().setEmail("chef@exploitation.cg");
            app.getSecurity().getBootstrapAdmin().setPassword("UnAutreMotDePasse!");
            Mockito.when(roleUserService.hasAnyUserAssignedToRole(anyString())).thenReturn(false);

            run();

            ArgumentCaptor<UserRequest> captor = ArgumentCaptor.forClass(UserRequest.class);
            Mockito.verify(provisioningService).initializeGlobalAdmin(captor.capture());

            assertThat(captor.getValue().getEmail()).isEqualTo("chef@exploitation.cg");
            assertThat(captor.getValue().getPassword()).isEqualTo("UnAutreMotDePasse!");
        }
    }

    // ============================================================
    // Idempotence
    // ============================================================

    @Nested
    @DisplayName("Idempotence")
    class Idempotence {

        /**
         * <strong>La garantie la plus importante.</strong> Le composant s'exécute à
         * <em>chaque</em> démarrage. S'il réappliquait le mot de passe de
         * configuration, un développeur qui aurait changé le sien le verrait
         * silencieusement rétabli au redémarrage suivant — et chercherait longtemps
         * pourquoi sa nouvelle valeur ne fonctionne plus.
         *
         * <p>C'est la différence entre un amorçage et un écrasement.
         */
        @Test
        @DisplayName("un administrateur existant n'est ni recréé ni réinitialisé")
        void existingAdminIsLeftAlone() {
            Mockito.when(roleUserService.hasAnyUserAssignedToRole(anyString())).thenReturn(true);

            run();

            Mockito.verify(provisioningService, Mockito.never()).initializeGlobalAdmin(any());
        }

        @Test
        @DisplayName("dix démarrages sur base vierge ne créent qu'un compte")
        void repeatedStartupsCreateOnce() {
            // Première exécution : la base est vierge. Les suivantes voient le compte.
            Mockito.when(roleUserService.hasAnyUserAssignedToRole(anyString()))
                    .thenReturn(false, true, true, true, true, true, true, true, true, true);

            for (int i = 0; i < 10; i++) {
                run();
            }

            Mockito.verify(provisioningService, Mockito.times(1)).initializeGlobalAdmin(any());
        }
    }

    // ============================================================
    // Il ne peut pas empêcher le démarrage
    // ============================================================

    @Nested
    @DisplayName("Il ne peut jamais empêcher le démarrage")
    class NeverBlocksStartup {

        /**
         * Une base momentanément incohérente — migration à moitié appliquée, table
         * verrouillée, rôle {@code ADMIN} absent — ne doit pas rendre l'application
         * impossible à lancer, <em>précisément</em> au moment où l'on cherche à la
         * réparer. C'est un confort de développement, pas une dépendance.
         */
        @Test
        @DisplayName("un échec de l'amorçage est avalé")
        void seedingFailureIsSwallowed() {
            Mockito.when(roleUserService.hasAnyUserAssignedToRole(anyString())).thenReturn(false);
            Mockito.when(provisioningService.initializeGlobalAdmin(any()))
                    .thenThrow(new IllegalStateException("rôle ADMIN introuvable"));

            assertThatCode(this::runSeeder).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("une base injoignable est avalée aussi")
        void repositoryFailureIsSwallowed() {
            Mockito.when(roleUserService.hasAnyUserAssignedToRole(anyString()))
                    .thenThrow(new RuntimeException("connexion perdue"));

            assertThatCode(this::runSeeder).doesNotThrowAnyException();
            Mockito.verify(provisioningService, Mockito.never()).initializeGlobalAdmin(any());
        }

        private void runSeeder() {
            DefaultAdminSeederTest.this.run();
        }
    }

    // ============================================================
    // Interrupteur et refus en production
    // ============================================================

    @Nested
    @DisplayName("Interrupteur et garde de production")
    class SwitchAndProdGuard {

        @Test
        @DisplayName("désactivé, rien n'est créé ni même interrogé")
        void disabledDoesNothing() {
            app.getSecurity().getBootstrapAdmin().setEnabled(false);

            run();

            Mockito.verifyNoInteractions(provisioningService, roleUserService);
        }

        /**
         * {@code @Profile("dev")} empêche déjà ce bean d'exister en production. Ce
         * test fige la <strong>ceinture par-dessus les bretelles</strong> : le jour où
         * quelqu'un élargirait l'annotation sans mesurer ce qu'il ouvre, le composant
         * refuse quand même — plutôt que de créer un administrateur au mot de passe
         * public dans un système qui gère des données économiques d'exploitation.
         */
        @Test
        @DisplayName("en profil prod, l'amorçage est REFUSÉ même activé")
        void productionProfileIsRefused() {
            environment.setActiveProfiles("prod");
            app.getSecurity().getBootstrapAdmin().setEnabled(true);

            run();

            Mockito.verifyNoInteractions(provisioningService, roleUserService);
        }

        @Test
        @DisplayName("un profil mêlant dev et prod est refusé aussi")
        void mixedProfileWithProdIsRefused() {
            environment.setActiveProfiles("dev", "prod");

            run();

            Mockito.verifyNoInteractions(provisioningService, roleUserService);
        }
    }
}
