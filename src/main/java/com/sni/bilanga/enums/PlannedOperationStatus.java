package com.sni.bilanga.enums;

/**
 * Où en est une opération prévue de l'itinéraire technique.
 *
 * <p>Miroir exact de {@code chk_planned_op_status} (V29).
 *
 * <p><strong>Ce que cette énumération ne porte PAS, et pourquoi.</strong> Il n'existe
 * pas de valeur {@code EN_RETARD}. Le retard est un état <em>dérivé</em> — date prévue
 * dépassée et aucune intervention rapprochée — et le projet n'a ni ordonnanceur ni
 * tâche de fond pour l'entretenir. Persisté, il serait faux dès le lendemain de son
 * écriture ; il est donc calculé à chaque lecture.
 */
public enum PlannedOperationStatus {

    /** Planifiée, rien de constaté. L'état initial. */
    PREVUE("Prévue"),

    /** Une intervention réelle l'a satisfaite. */
    REALISEE("Réalisée"),

    /**
     * Faite, mais pas comme prévu — dose réduite, date décalée, produit substitué.
     *
     * <p>Distincte de {@code REALISEE} parce qu'elle explique un écart de résultat que
     * rien d'autre n'expliquerait : un traitement fait à demi-dose n'est pas un
     * traitement fait.
     */
    PARTIELLE("Partielle"),

    /** Décidé de ne pas la faire. Ce n'est ni un oubli, ni un retard. */
    ABANDONNEE("Abandonnée");

    private final String label;

    PlannedOperationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Vrai si l'opération n'attend plus rien — elle ne peut plus être en retard. */
    public boolean isSettled() {
        return this != PREVUE;
    }

    public static PlannedOperationStatus from(String value) {
        return DomainEnums.parse(PlannedOperationStatus.class, value);
    }
}
