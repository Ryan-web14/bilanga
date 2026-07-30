package com.sni.bilanga.security.authorization;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Vocabulaire des permissions de Bilanga — source unique de vérité.
 *
 * <p><strong>Pourquoi cette énumération existe.</strong> Les permissions étaient
 * des chaînes libres, répétées dans trois endroits sans lien : les
 * {@code @PreAuthorize} des contrôleurs, la table de correspondance de
 * {@link AdminApiAuthorizationManager}, et les lignes semées en base. Rien ne
 * garantissait qu'elles coïncident — et de fait, la base était vide pendant que
 * le code exigeait {@code SYSTEM:USERS}. Le contrôle d'accès était écrit et
 * entièrement inopérant.
 *
 * <p>Ici, le nom se lit à un seul endroit. La migration V24 sème exactement ces
 * valeurs, et le gestionnaire d'autorisation ne peut en exiger d'autres.
 *
 * <p><strong>Ce qui a disparu.</strong> {@code BILLING}, {@code PAYMENT},
 * {@code KYC}, {@code CASH}, {@code BOOKING}, {@code DOCUMENT},
 * {@code SUBSCRIPTION}, {@code INVENTORY}, {@code VISITOR}, {@code CRM} — une
 * trentaine de permissions héritées d'un projet de finance. Aucune route de
 * Bilanga ne leur correspondait. Les conserver donnait à lire un modèle de
 * droits qui n'existait pas, et masquait l'absence de celui qui manquait
 * vraiment.
 *
 * <p><strong>Les deux niveaux à ne pas confondre.</strong> Ces permissions
 * disent <em>quelles routes</em> un compte peut appeler. Le rôle
 * d'appartenance à une exploitation ({@code MembershipRole}, migration V22) dit
 * <em>quelles parcelles</em> il peut voir et <em>quels domaines de données</em>
 * il peut y lire. Les deux se composent : une permission ouvre la route,
 * {@code AccessGuard} filtre les données.
 */
public enum AppPermission {

    // ── Système ─────────────────────────────────────────────────
    SYSTEM_USERS("SYSTEM", "USERS", "Gestion des utilisateurs"),
    SYSTEM_ROLES("SYSTEM", "ROLES", "Gestion des rôles"),
    SYSTEM_PERMISSIONS("SYSTEM", "PERMISSIONS", "Gestion des permissions"),
    SYSTEM_AUDIT("SYSTEM", "AUDIT", "Consultation des journaux"),
    SYSTEM_SETTINGS("SYSTEM", "SETTINGS", "Configuration du système"),
    SYSTEM_NOTIFICATIONS("SYSTEM", "NOTIFICATIONS", "Supervision des envois"),

    /** Laissez-passer générique vers l'administration, pour les routes non cartographiées. */
    ADMIN_ACCESS("ADMIN", "ACCESS", "Accès à l'administration"),

    // ── Organisation : coopératives et exploitations ────────────
    ORGANIZATION_READ("ORGANIZATION", "READ", "Consulter les exploitations"),
    ORGANIZATION_CREATE("ORGANIZATION", "CREATE", "Créer une exploitation"),
    ORGANIZATION_UPDATE("ORGANIZATION", "UPDATE", "Modifier une exploitation"),
    ORGANIZATION_DELETE("ORGANIZATION", "DELETE", "Archiver une exploitation"),

    // ── Parcelles et cultures ───────────────────────────────────
    FARM_READ("FARM", "READ", "Consulter parcelles et cultures"),
    FARM_CREATE("FARM", "CREATE", "Créer une parcelle ou une culture"),
    FARM_UPDATE("FARM", "UPDATE", "Modifier une parcelle ou une culture"),
    FARM_DELETE("FARM", "DELETE", "Archiver une parcelle ou une culture"),

    // ── Matériel de terrain ─────────────────────────────────────
    IOT_READ("IOT", "READ", "Consulter boîtiers, capteurs et relevés"),
    IOT_CREATE("IOT", "CREATE", "Enregistrer un boîtier, un capteur, un relevé"),
    IOT_UPDATE("IOT", "UPDATE", "Modifier le parc de terrain"),
    IOT_DELETE("IOT", "DELETE", "Retirer du matériel"),

    // ── Diagnostics, conseils, alertes ──────────────────────────
    DIAGNOSIS_READ("DIAGNOSIS", "READ", "Consulter diagnostics, conseils et alertes"),
    DIAGNOSIS_CREATE("DIAGNOSIS", "CREATE", "Lancer un diagnostic"),
    DIAGNOSIS_UPDATE("DIAGNOSIS", "UPDATE", "Acquitter, résoudre, affecter, donner suite"),
    DIAGNOSIS_DELETE("DIAGNOSIS", "DELETE", "Supprimer un diagnostic"),

    // ── Base de connaissance agronomique ────────────────────────
    KNOWLEDGE_READ("KNOWLEDGE", "READ", "Consulter les seuils et les règles"),
    KNOWLEDGE_CREATE("KNOWLEDGE", "CREATE", "Ajouter un seuil, une maladie, une règle"),
    KNOWLEDGE_UPDATE("KNOWLEDGE", "UPDATE", "Ajuster les seuils agronomiques"),
    KNOWLEDGE_DELETE("KNOWLEDGE", "DELETE", "Retirer une règle"),

    // ── Journal des interventions ───────────────────────────────
    INTERVENTION_READ("INTERVENTION", "READ", "Consulter les interventions"),
    INTERVENTION_CREATE("INTERVENTION", "CREATE", "Déclarer une intervention"),
    INTERVENTION_UPDATE("INTERVENTION", "UPDATE", "Corriger une intervention"),
    INTERVENTION_DELETE("INTERVENTION", "DELETE", "Supprimer une intervention"),

    // ── Récoltes et économie ────────────────────────────────────
    HARVEST_READ("HARVEST", "READ", "Consulter récoltes et rendements"),
    HARVEST_CREATE("HARVEST", "CREATE", "Enregistrer une récolte"),
    HARVEST_UPDATE("HARVEST", "UPDATE", "Corriger une récolte"),
    HARVEST_DELETE("HARVEST", "DELETE", "Supprimer une récolte"),

    // ── Tableaux de bord ────────────────────────────────────────
    OVERVIEW_READ("OVERVIEW", "READ", "Consulter les tableaux de bord");

    private final String module;
    private final String action;
    private final String displayName;

    AppPermission(String module, String action, String displayName) {
        this.module = module;
        this.action = action;
        this.displayName = displayName;
    }

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Forme portée par les autorités Spring et citée dans {@code @PreAuthorize} : {@code MODULE:ACTION}. */
    public String authority() {
        return module + ":" + action;
    }

    public static AppPermission from(String authority) {
        if (authority == null || authority.isBlank()) {
            return null;
        }
        String normalized = authority.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(p -> p.authority().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** Toutes les permissions d'un module, dans l'ordre de déclaration. */
    public static List<AppPermission> ofModule(String module) {
        return Arrays.stream(values())
                .filter(p -> p.getModule().equalsIgnoreCase(module))
                .toList();
    }
}
