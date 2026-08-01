package com.sni.bilanga.diagnosis.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Rejeu d'un diagnostic passé avec les seuils <strong>actuels</strong>.
 *
 * <h2>Ce que cela permet, et que rien d'autre ne permettait</h2>
 *
 * <p>La base de connaissance est pilotable par API : un agronome peut ajuster un
 * seuil d'humidité de 35 à 32 %. Mais rien ne lui disait <em>ce que cela
 * change</em>. Il modifiait, puis attendait le prochain relevé pour voir — sur des
 * conditions différentes de celles qui l'avaient interrogé.
 *
 * <p>Le rejeu transforme la base de connaissance en objet <strong>expérimentable</strong> :
 * « qu'aurait dit le système, sur ce relevé précis, si ce seuil avait été à 32 % ? ».
 * La question devient vérifiable au lieu d'être théorique.
 *
 * <p><strong>Rien n'a eu besoin d'être ajouté pour cela.</strong> Le relevé est en
 * base, le diagnostic aussi, et les recommandations portent leurs colonnes de
 * traçabilité depuis la V9. Il ne manquait que de rejouer le pipeline sur des
 * données déjà là.
 *
 * <h2>Ce que le rejeu ne fait pas</h2>
 *
 * <p><strong>Il n'écrit rien.</strong> Aucun diagnostic, aucune recommandation,
 * aucune alerte. C'est une simulation, et une simulation qui laisserait des traces
 * polluerait l'historique de la parcelle avec des situations qui n'ont pas eu lieu
 * — puis les compterait dans les statistiques de suivi des conseils.
 *
 * <p><strong>Il ne rappelle pas le modèle d'image.</strong> Sur un diagnostic
 * IMAGE, la photo n'est pas conservée. Le rejeu part donc du résultat retenu à
 * l'époque et rejoue les <em>moteurs</em> sur le relevé : c'est la moitié du
 * pipeline qui dépend de la base de connaissance, donc exactement celle qu'on
 * cherche à éprouver.
 *
 * <p><strong>Il ne dit pas qui a raison.</strong> Un écart entre l'original et le
 * rejeu constate que la connaissance a changé ; il ne dit pas si elle a changé en
 * mieux. C'est à l'agronome de trancher, et {@code limitation} le rappelle.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DiagnosisReplay {

    private Long diagnosticId;
    private Long plotId;
    private String plotName;
    private String cropName;
    private String source;

    /** Relevé rejoué, et sa date : c'est lui qui rend le rejeu reproductible. */
    private Long readingId;
    private Instant readingRecordedAt;

    /** Date du diagnostic d'origine, pour situer l'écart dans le temps. */
    private Instant originalDiagnosedAt;

    /** Conclusion et conseils tels qu'ils ont été émis, relus depuis la base. */
    private Snapshot original;

    /** Conclusion et conseils que la connaissance actuelle produirait. */
    private Snapshot replayed;

    /**
     * Ce qui a changé, formulé en français.
     *
     * <p>Vide lorsque la connaissance n'a pas bougé pour ce cas — c'est
     * l'information la plus fréquente et la plus rassurante : un ajustement de
     * seuil ailleurs n'a pas d'effet ici.
     */
    private List<Difference> differences;

    /** Vrai si {@code differences} est vide, pour éviter un test côté client. */
    private Boolean identical;

    /** Résumé rédigé, prêt à afficher. */
    private String summary;

    /**
     * Réserve, toujours renseignée.
     *
     * <p>Un écart constate que la connaissance a changé, non qu'elle a changé en
     * mieux. Le taire ferait lire le rejeu comme une validation.
     */
    private String limitation;

    private Instant generatedAt;

    /**
     * État d'un diagnostic : sa conclusion et ses conseils.
     *
     * <p>Volontairement plat et identique des deux côtés : c'est ce qui permet de
     * les afficher côte à côte sans transformation.
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class Snapshot {

        private String result;

        /**
         * Nom français du résultat.
         *
         * <p>Le modèle rend une classe anglaise issue de son jeu d'entraînement. Cet
         * instantané est le seul objet du diagnostic qui présente le résultat
         * <strong>à côté de son score de confiance</strong> : le laisser en anglais
         * remettait un code brut au premier endroit que l'utilisateur lit.
         */
        private String resultLabel;

        private Double confidenceScore;
        private String confidenceLevel;
        private Boolean reliable;

        private Integer recommendationCount;

        /** Conseils, du plus prioritaire au moins prioritaire. */
        private List<Line> recommendations;

        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @Data
        public static class Line {
            private String content;
            private String type;
            private String priority;
            private String category;

            /** Traçabilité, pour comparer non seulement les textes mais les seuils. */
            private String measureField;
            private Double observedValue;
            private Double thresholdValue;
        }
    }

    /**
     * Un écart entre l'original et le rejeu.
     *
     * @param kind        {@code CONSEIL_AJOUTE}, {@code CONSEIL_RETIRE},
     *                    {@code SEUIL_MODIFIE} ou {@code PRIORITE_MODIFIEE}
     * @param statement   formulation française, prête à afficher
     * @param category    domaine concerné, pour regrouper les écarts
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class Difference {
        private String kind;
        private String kindLabel;
        private String category;
        private String statement;
    }
}
