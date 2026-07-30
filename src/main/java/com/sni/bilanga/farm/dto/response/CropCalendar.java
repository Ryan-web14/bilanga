package com.sni.bilanga.farm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Calendrier cultural d'une campagne : les stades franchis et ceux à venir.
 *
 * <h2>Pourquoi cela n'existait pas, alors que tout était calculé</h2>
 *
 * <p>{@code GrowthStageResolver.stageTimeline} reconstitue depuis toujours la date
 * de début de <strong>chaque</strong> stade, passés comme à venir — c'est une
 * fonction déterministe de la date de plantation et de la durée du cycle. Mais
 * deux consommateurs seulement s'en servaient, et tous deux ne regardaient que le
 * passé : la chronologie de la parcelle, qui écarte explicitement les stades
 * futurs pour ne pas mêler le constaté au prévu, et le recalcul du stade courant.
 *
 * <p>Le résultat est que le système <em>savait</em> qu'une floraison était attendue
 * dans neuf jours et ne le disait à personne. Or c'est précisément l'information
 * qui permet d'agir <strong>avant</strong> : un traitement préventif se pose à
 * l'entrée en floraison, pas quand la maladie est détectée. Le reste du système est
 * réactif par construction — il constate une mesure, un symptôme, un écart. Cette
 * vue est la seule qui annonce.
 *
 * <p>C'est donc du travail de restitution, non de moteur : aucun calcul nouveau,
 * aucune migration, aucune colonne.
 *
 * <h2>La réserve, et pourquoi elle est portée par la réponse</h2>
 *
 * <p>Ces dates sont des <strong>projections</strong>, issues de proportions de
 * cycle indicatives. Une levée lente, une sécheresse, une variété mal identifiée
 * les décalent toutes. Affichées sans réserve, elles seraient lues comme un
 * calendrier agronomique établi — et un exploitant qui prépare un traitement pour
 * une date fausse perd le produit et la fenêtre.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CropCalendar {

    private Long cropId;
    private Long plotId;
    private String plotName;

    private String cropName;
    private String variety;

    private LocalDate plantingDate;
    private Integer cycleDurationDays;
    private LocalDate expectedHarvestDate;

    /** Stade en cours, tel que le moteur agronomique l'emploie aujourd'hui. */
    private String currentStage;
    private String currentStageLabel;

    /** Jours écoulés depuis la plantation. Nombre, non chaîne. */
    private Integer daysSincePlanting;

    /**
     * Jours restants avant la récolte attendue. <strong>Négatif si le terme est
     * dépassé</strong> — c'est le cas à mettre en avant, pas à masquer.
     */
    private Integer daysToHarvest;

    /** Avancement dans le cycle, de 0 à 1 et au-delà si le terme est franchi. */
    private Double cycleProgress;

    /** Du plus ancien au plus récent, passés puis à venir. */
    private List<StageWindow> stages;

    /**
     * Prochain stade attendu, extrait pour l'affichage.
     *
     * <p>{@code null} lorsque le cycle est achevé : il n'y a alors plus rien à
     * annoncer, et fabriquer un stade suivant laisserait croire que la campagne
     * continue.
     */
    private StageWindow nextStage;

    /**
     * Réserve, <strong>toujours renseignée</strong>.
     *
     * <p>À afficher à côté des dates, pas dans un repli : ce sont des projections
     * calculées sur des proportions de cycle indicatives, et un exploitant qui
     * prépare un traitement pour une date fausse perd le produit et la fenêtre.
     */
    private String limitation;

    /**
     * Une phase du cycle, datée.
     *
     * @param stage      constante du vocabulaire {@code GrowthStage}
     * @param label      libellé français, prêt à afficher
     * @param startsOn   début, reconstitué depuis la plantation
     * @param endsOn     dernier jour de la phase ; {@code null} pour la dernière,
     *                   qui s'achève à la récolte
     * @param past       la phase est achevée
     * @param current    la phase est celle d'aujourd'hui
     * @param daysUntil  jours avant son début ; négatif si elle a commencé, ce qui
     *                   permet d'écrire « commencée depuis 4 jours » sans second calcul
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class StageWindow {

        private String stage;
        private String label;
        private LocalDate startsOn;
        private LocalDate endsOn;

        private Boolean past;
        private Boolean current;

        private Integer daysUntil;
    }
}
