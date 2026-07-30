package com.sni.bilanga.security.access;

import java.util.function.Supplier;

/**
 * Marque le fil d'exécution comme agissant pour un <strong>appareil</strong>, et non pour
 * une personne.
 *
 * <h2>Le défaut que cela corrige</h2>
 *
 * <p>L'ingestion s'authentifie par <strong>clé partagée</strong> — un microcontrôleur n'a
 * ni la mémoire ni l'horloge pour gérer un cycle de vie de jeton. Il n'y a donc, sur ce
 * chemin, <em>aucun utilisateur authentifié</em>, et c'est délibéré.
 *
 * <p>Mais le diagnostic déclenché par un relevé traverse
 * {@code ContextResolver → PlotService.require → AccessGuard.requireAccess}. Avec
 * {@code ownership.enabled=true}, l'appelant anonyme y était refusé :
 *
 * <pre>ForbiddenException: Cette parcelle ne vous est pas accessible.</pre>
 *
 * <p><strong>Conséquence : en production, aucun relevé ne produisait de diagnostic.</strong>
 * Pas une fraction — tous. Le cloisonnement, qui doit protéger sans enfermer, enfermait la
 * chaîne capteur toute entière.
 *
 * <h2>Pourquoi un marqueur explicite plutôt que « anonyme ⇒ autorisé »</h2>
 *
 * <p>Traiter l'absence d'authentification comme un laissez-passer serait une règle
 * dangereuse : elle vaudrait pour toute route ouverte, présente ou future, et personne ne
 * s'en apercevrait avant qu'une nouvelle route publique ne touche une parcelle.
 *
 * <p>Ici l'intention est <strong>déclarée</strong>, sur une portée bornée : seul le chemin
 * d'ingestion l'ouvre, il le referme dans un {@code finally}, et l'étendre demande d'écrire
 * ce nom quelque part — donc de se poser la question.
 *
 * <p><strong>Ce que cela n'ouvre pas</strong> : la clé d'ingestion reste exigée en amont,
 * et le relevé reste rattaché au boîtier, donc à sa parcelle. Un appareil ne choisit pas
 * la parcelle sur laquelle il dépose — elle se déduit de son identité matérielle.
 */
public final class MachineContext {

    /**
     * {@code ThreadLocal} et non un paramètre : le marqueur doit traverser six couches
     * — service d'ingestion, service de diagnostic, résolveur de contexte, service de
     * parcelle, garde d'accès — dont aucune n'a de raison métier de connaître la notion.
     * Le faire descendre en paramètre les polluerait toutes.
     *
     * <p>Même patron que {@code AuditContext}, déjà employé dans ce projet.
     */
    private static final ThreadLocal<Boolean> DEVICE = new ThreadLocal<>();

    private MachineContext() {
    }

    /** Vrai si le fil courant agit pour un appareil authentifié par clé. */
    public static boolean isDevice() {
        return Boolean.TRUE.equals(DEVICE.get());
    }

    /**
     * Exécute une action au nom d'un appareil.
     *
     * <p>Le {@code finally} n'est pas une précaution de style : les fils sont
     * <strong>réutilisés</strong> par le conteneur de servlets. Un marqueur oublié
     * s'appliquerait à la requête suivante, celle d'un utilisateur — et lui accorderait
     * silencieusement un accès qu'il n'a pas.
     *
     * <p>La valeur précédente est restaurée plutôt que simplement effacée : un appel
     * imbriqué ne doit pas révoquer le marqueur de son appelant.
     */
    public static <T> T asDevice(Supplier<T> action) {
        Boolean previous = DEVICE.get();
        DEVICE.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                DEVICE.remove();
            } else {
                DEVICE.set(previous);
            }
        }
    }
}
