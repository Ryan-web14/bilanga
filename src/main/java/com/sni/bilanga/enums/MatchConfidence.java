package com.sni.bilanga.enums;

/**
 * Quelle foi accorder au rapprochement entre une opération prévue et une intervention
 * réellement menée.
 *
 * <p><strong>Pourquoi une confiance, et non un simple booléen.</strong> Le
 * rapprochement est une <em>inférence</em> : rien, dans les données, ne dit qu'une
 * fertilisation du 14 mai est celle qui était prévue le 12. On le suppose parce que
 * c'est la lecture la plus économique — et il faut que l'exploitant puisse voir la
 * différence entre « c'est certainement celle-là » et « c'est probablement celle-là ».
 *
 * <p>Miroir exact de {@code chk_planned_op_confidence} (V29).
 */
public enum MatchConfidence {

    /**
     * À deux jours près de la date prévue.
     *
     * <p>La tolérance n'est pas nulle : une opération se décale d'un jour pour une
     * pluie ou une indisponibilité, sans cesser d'être celle qui était prévue.
     */
    EXACTE("Exacte", 2),

    /**
     * À dix jours près.
     *
     * <p>Assez large pour couvrir un report ordinaire, assez étroit pour ne pas
     * rapprocher deux opérations d'un même type séparées par un mois.
     */
    PROBABLE("Probable", 10),

    /**
     * Confirmé par un humain.
     *
     * <p>Le seul cas qui s'écrit en base. Les deux autres sont recalculés à chaque
     * lecture : un mauvais rapprochement qui se persiste devra être corrigé à la main,
     * là où un mauvais rapprochement qui se recalcule disparaît dès que la donnée
     * s'améliore.
     */
    MANUELLE("Confirmée manuellement", Integer.MAX_VALUE);

    private final String label;

    /** Écart maximal, en jours, entre la date prévue et la date constatée. */
    private final int toleranceDays;

    MatchConfidence(String label, int toleranceDays) {
        this.label = label;
        this.toleranceDays = toleranceDays;
    }

    public String getLabel() {
        return label;
    }

    public int getToleranceDays() {
        return toleranceDays;
    }

    /**
     * Confiance déduite d'un écart en jours, ou {@code null} au-delà de toute
     * tolérance — auquel cas il n'y a pas de rapprochement du tout.
     */
    public static MatchConfidence forGap(long days) {
        long gap = Math.abs(days);
        if (gap <= EXACTE.toleranceDays) {
            return EXACTE;
        }
        return gap <= PROBABLE.toleranceDays ? PROBABLE : null;
    }

    public static MatchConfidence from(String value) {
        return DomainEnums.parse(MatchConfidence.class, value);
    }
}
