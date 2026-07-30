package com.sni.bilanga.overview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Chronologie unifiée d'une parcelle : ce qui lui est arrivé, dans l'ordre.
 *
 * <p><strong>Ce que cela remplace.</strong> Reconstituer l'histoire d'une
 * parcelle demandait quatre appels — relevés, diagnostics, alertes,
 * observations — puis une fusion et un tri côté client. Chaque client la
 * refaisait à sa façon, avec ses propres décisions sur ce qui mérite d'y
 * figurer. Or c'est <em>la</em> vue qui explique une situation : un diagnostic
 * pris trois jours après un changement de stade et deux jours après un
 * traitement ne se lit pas comme le même diagnostic isolé.
 *
 * <p><strong>Ce qui n'y figure pas.</strong> Les relevés nominaux. Une parcelle
 * instrumentée en produit des milliers ; les verser tous rendrait la
 * chronologie illisible et noierait ce qu'on y cherche. Seuls les relevés
 * marquants — anomalie matérielle constatée — y entrent.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PlotTimeline {

    private Long plotId;
    private String plotName;
    private String plotCode;

    private Instant from;
    private Instant to;

    /** Nombre d'entrées de la page courante. */
    private Integer entryCount;

    /** Total toutes pages confondues, pour dimensionner la navigation. */
    private Integer totalEntries;

    /** Décompte par nature, sur la fenêtre entière et non sur la page. */
    private Map<String, Integer> countsByType;

    /** Natures effectivement interrogées — utile quand un filtre a été transmis. */
    private List<String> requestedTypes;

    /**
     * Vrai lorsque au moins une source a atteint son plafond : la fenêtre
     * demandée contient <strong>plus</strong> d'événements que ce qui est rendu.
     *
     * <p>Sans ce drapeau, une chronologie plafonnée se lisait exactement comme
     * une chronologie complète, et {@code totalEntries} paraissait être le total
     * réel. Le client concluait « il n'y a eu que deux cents diagnostics » alors
     * que c'est la borne, et non les faits, qui avait décidé de la dernière
     * ligne. Sur une vue dont l'objet est de raconter ce qui s'est passé, laisser
     * croire à l'exhaustivité est une erreur de fond.
     *
     * <p>À afficher : « affichage limité aux {@code perSourceLimit} événements
     * les plus récents par nature — resserrez la période pour tout voir ».
     */
    private Boolean truncated;

    /** Plafond appliqué par nature d'événement, pour formuler le message. */
    private Integer perSourceLimit;

    /**
     * Natures effectivement plafonnées.
     *
     * <p>Dire <em>laquelle</em> est incomplète, et non seulement que quelque
     * chose l'est : resserrer la fenêtre n'a d'intérêt que si l'on sait ce qui
     * débordait.
     */
    private List<String> truncatedTypes;

    private List<TimelineEntry> entries;

    /**
     * Une chose qui est arrivée à la parcelle.
     *
     * <p>Volontairement plat et uniforme : la chronologie sert à être parcourue,
     * pas à porter le détail de chaque objet. {@code refType} et {@code refId}
     * disent où aller chercher ce détail quand il est demandé.
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class TimelineEntry {

        private Instant occurredAt;

        /** Vocabulaire de {@code TimelineEventType}. */
        private String type;
        private String typeLabel;

        /** Formulation courte, directement affichable. */
        private String title;

        /** Complément, ou {@code null} si le titre se suffit. */
        private String detail;

        /**
         * Gravité perçue : {@code INFO}, {@code ATTENTION}, {@code CRITIQUE}.
         *
         * Portée par la chronologie plutôt que déduite du type : une alerte
         * moyenne et une alerte critique sont toutes deux des alertes, et il
         * serait faux de les afficher pareillement.
         */
        private String severity;

        /** Ressource d'origine, pour aller au détail. */
        private String refType;
        private Long refId;

        /** Auteur, quand l'entrée résulte d'une action humaine. */
        private String actor;
    }
}
