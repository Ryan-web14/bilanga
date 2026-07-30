package com.sni.bilanga.security.authorization;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.sni.bilanga.security.authorization.AppPermission.*;

/**
 * Rôles de la plateforme — source unique de vérité, semée par la migration V24.
 *
 * <p><strong>Ce que ces rôles disent, et ce qu'ils ne disent pas.</strong> Ils
 * gouvernent l'accès aux <em>routes</em> : qui peut appeler quoi. Ils ne disent
 * rien de <em>quelles parcelles</em> chacun voit — cela relève de
 * {@code AccessGuard} et, pour les membres d'une exploitation, de
 * {@code MembershipRole}.
 *
 * <p>Un {@link #EXPLOITANT} porte {@code FARM:READ}, donc il peut appeler
 * {@code GET /plots} ; {@code AccessGuard} décide ensuite <em>lesquelles</em>
 * lui reviennent. Les deux mécanismes se composent, et aucun ne remplace
 * l'autre.
 *
 * <p><strong>⚠️ Deux vocabulaires distincts, à ne jamais confondre.</strong>
 * {@code TECHNICIEN} existe ici <em>et</em> dans {@code MembershipRole}. Ce
 * n'est pas une redondance : le premier dit « ce compte administre du matériel
 * sur la plateforme », le second « cette personne intervient sur le matériel de
 * cette exploitation-là ». Les deux se cumulent naturellement.
 *
 * <p><strong>Ce qui a été renommé.</strong> {@code STAFF} et {@code USER},
 * hérités d'un projet de finance, ne voulaient rien dire pour une plateforme
 * agricole : ils sont devenus {@link #AGRONOME} et {@link #EXPLOITANT}, qui
 * désignent des métiers réels et dont les droits en découlent.
 */
public enum SecurityRole {

    /**
     * Accès total, sans vérification de permission.
     *
     * {@code AdminApiAuthorizationManager} le laisse passer avant toute autre
     * règle. À réserver à un seul compte : c'est le seul rôle qui puisse
     * atteindre une route non cartographiée.
     */
    SUPER_ADMIN("Super administrateur",
            "Accès total, sans contrôle de permission. Un seul compte devrait le porter.",
            Arrays.asList(values0())),

    /** Administration complète de la plateforme. */
    ADMIN("Administrateur",
            "Administre la plateforme : comptes, rôles, permissions, journaux, exploitations.",
            Arrays.asList(values0())),

    /**
     * Expert agronomique. Pilote la base de connaissance — c'est lui qui ajuste
     * les seuils dont dépendent tous les diagnostics.
     *
     * Aucun droit système : il ne crée pas de comptes et ne lit pas les
     * journaux d'audit.
     */
    AGRONOME("Agronome",
            "Pilote la base de connaissance et suit les diagnostics. Aucun droit système.",
            List.of(FARM_READ, FARM_CREATE, FARM_UPDATE,
                    IOT_READ,
                    DIAGNOSIS_READ, DIAGNOSIS_CREATE, DIAGNOSIS_UPDATE,
                    KNOWLEDGE_READ, KNOWLEDGE_CREATE, KNOWLEDGE_UPDATE, KNOWLEDGE_DELETE,
                    INTERVENTION_READ, INTERVENTION_CREATE, INTERVENTION_UPDATE,
                    HARVEST_READ,
                    ORGANIZATION_READ,
                    OVERVIEW_READ)),

    /**
     * Responsable du parc de terrain.
     *
     * Voit et administre le matériel, lit les parcelles pour savoir où il se
     * trouve — et rien d'autre. Ni diagnostics, ni conseils, ni économie :
     * réparer une sonde ne demande pas de savoir ce qu'elle mesure.
     */
    TECHNICIEN("Technicien",
            "Administre le parc de boîtiers et de capteurs. Ni agronomie, ni économie.",
            List.of(IOT_READ, IOT_CREATE, IOT_UPDATE, IOT_DELETE,
                    FARM_READ,
                    OVERVIEW_READ)),

    /**
     * L'agriculteur. Rôle par défaut de tout compte créé sans rôle explicite —
     * le moindre privilège est le bon défaut.
     *
     * Il dispose de tout le métier sur <em>ses</em> parcelles, le cloisonnement
     * étant assuré par {@code AccessGuard}. Il lit la base de connaissance sans
     * pouvoir la modifier : les seuils engagent toutes les exploitations.
     */
    EXPLOITANT("Exploitant",
            "Agriculteur. Accède à ses propres parcelles et à tout leur suivi.",
            List.of(FARM_READ, FARM_CREATE, FARM_UPDATE, FARM_DELETE,
                    IOT_READ, IOT_CREATE, IOT_UPDATE,
                    DIAGNOSIS_READ, DIAGNOSIS_CREATE, DIAGNOSIS_UPDATE,
                    KNOWLEDGE_READ,
                    INTERVENTION_READ, INTERVENTION_CREATE, INTERVENTION_UPDATE, INTERVENTION_DELETE,
                    HARVEST_READ, HARVEST_CREATE, HARVEST_UPDATE, HARVEST_DELETE,
                    ORGANIZATION_READ,
                    OVERVIEW_READ));

    /** Rôle attribué à un compte créé sans rôle explicite : le plus restreint. */
    public static final SecurityRole DEFAULT = EXPLOITANT;

    /** Rôles considérés comme privilégiés par {@code AccessGuard}. */
    public static final Set<String> PRIVILEGED_AUTHORITIES =
            Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN");

    private final String displayName;
    private final String description;
    private final List<AppPermission> permissions;

    SecurityRole(String displayName, String description, List<AppPermission> permissions) {
        this.displayName = displayName;
        this.description = description;
        this.permissions = permissions;
    }

    /** Toutes les permissions — évite de les énumérer deux fois pour SUPER_ADMIN et ADMIN. */
    private static AppPermission[] values0() {
        return AppPermission.values();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public List<AppPermission> getPermissions() {
        return permissions;
    }

    /** Forme portée par les autorités Spring : {@code ROLE_<NOM>}. */
    public String authority() {
        return "ROLE_" + name();
    }

    public Set<String> permissionAuthorities() {
        return permissions.stream()
                .map(AppPermission::authority)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public static SecurityRole from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(r -> r.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
