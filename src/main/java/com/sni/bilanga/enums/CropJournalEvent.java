package com.sni.bilanga.enums;

/**
 * Nature d'une entrée du journal de cycle.
 *
 * <p>Miroir exact de {@code chk_crop_journal_event} (V28) : l'énumération protège
 * l'API, la contrainte protège les données contre une écriture directe.
 *
 * <p><strong>Pourquoi ce vocabulaire et pas un simple booléen.</strong> Toutes les
 * écritures ne se lisent pas de la même façon. Un {@code STADE_RECALCULE} est un
 * effet du temps, pas une décision humaine — le confondre avec une
 * {@code MODIFICATION} ferait attribuer à quelqu'un un changement que personne n'a
 * demandé, et noierait les vraies modifications sous les recalculs automatiques.
 */
public enum CropJournalEvent {

    CREATION("Création"),

    /**
     * Modification par un utilisateur.
     *
     * <p>Ne consigne que ce qui a <strong>réellement</strong> changé : la mise à jour
     * est partielle, un champ absent de la requête n'est pas touché. Un
     * {@code valeur → null} décrit donc un effacement voulu, demandé par
     * {@code clearFields}.
     */
    MODIFICATION("Modification"),

    /**
     * Le stade a été réaligné sur la date de plantation par
     * {@code GrowthStageResolver}.
     *
     * <p>Volume borné, contrairement à ce qu'on pourrait craindre : {@code isStale}
     * compare le stade calculé au stade stocké, et le calcul est une fonction
     * déterministe du temps. La bascule a donc lieu <strong>au plus une fois par
     * stade</strong> — quatre ou cinq entrées par campagne, pas une par diagnostic.
     *
     * <p>Distinct de {@code MODIFICATION} parce que personne ne l'a décidé : c'est le
     * temps qui passe. Les mêler ferait porter à un utilisateur des changements qui ne
     * sont pas les siens.
     */
    STADE_RECALCULE("Stade recalculé"),

    CLOTURE("Clôture"),

    /** Cycle créé par clonage d'une campagne antérieure. */
    CLONAGE("Clonage");

    private final String label;

    CropJournalEvent(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Vrai si l'entrée résulte d'une décision humaine, et non du temps qui passe. */
    public boolean isHumanAction() {
        return this != STADE_RECALCULE;
    }

    public static CropJournalEvent from(String value) {
        return DomainEnums.parse(CropJournalEvent.class, value);
    }
}
