package com.sni.bilanga.enums;

/**
 * Titre auquel une personne accède aux données d'une exploitation.
 *
 * <p><strong>Le rôle n'est pas décoratif : il module l'accès.</strong> Un
 * technicien venu changer une sonde a besoin de voir l'état du matériel ; il n'a
 * aucune raison de voir les marges. Confondre les deux reviendrait à ouvrir la
 * comptabilité à quiconque intervient sur un boîtier — ce qui, dans un milieu où
 * tout le monde se connaît, est un problème social avant d'être technique.
 *
 * <p>Les droits sont volontairement <strong>en lecture</strong> dans un premier
 * temps. Ouvrir l'écriture par rôle demande d'arbitrer qui peut corriger la
 * saisie d'un autre, et cela ne se décide pas en même temps que le
 * cloisonnement.
 */
public enum MembershipRole {

    /** Répond de l'exploitation : accès complet, économique compris. */
    PROPRIETAIRE("Propriétaire", true, true, true),

    /**
     * Travaille sur les parcelles. Voit l'agronomique et le matériel, pas les
     * marges — non par défiance, mais parce que le résultat financier de
     * l'exploitation ne le concerne pas dans son travail.
     */
    OUVRIER("Ouvrier", false, true, true),

    /**
     * Agronome-conseil suivant l'exploitation sans en être propriétaire.
     * Voit tout le technique et l'agronomique ; l'économique appartient à
     * l'exploitant.
     */
    CONSEILLER("Conseiller", false, true, true),

    /**
     * Intervient sur le matériel. Voit l'état des boîtiers et la santé des
     * sondes, rien du contenu agronomique ni économique : réparer une sonde ne
     * demande pas de savoir ce qu'elle mesure, encore moins ce que la parcelle
     * rapporte.
     */
    TECHNICIEN("Technicien", false, false, true);

    private final String label;
    private final boolean economicAccess;
    private final boolean agronomicAccess;
    private final boolean technicalAccess;

    MembershipRole(String label, boolean economicAccess,
                   boolean agronomicAccess, boolean technicalAccess) {
        this.label = label;
        this.economicAccess = economicAccess;
        this.agronomicAccess = agronomicAccess;
        this.technicalAccess = technicalAccess;
    }

    public String getLabel() {
        return label;
    }

    public boolean allows(AccessScope scope) {
        return switch (scope) {
            case ECONOMIQUE -> economicAccess;
            case AGRONOMIQUE -> agronomicAccess;
            case TECHNIQUE -> technicalAccess;
        };
    }

    public static MembershipRole from(String value) {
        return DomainEnums.parse(MembershipRole.class, value);
    }
}
