package com.sni.bilanga.farm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sur quoi le moteur agronomique juge cette campagne — stade par stade.
 *
 * <h2>Le manque comblé</h2>
 *
 * <p>Le système compare chaque mesure à des seuils, en tire une sévérité, et produit un
 * conseil. L'exploitant voit le conseil et jamais le seuil. Quand le système annonce un
 * stress hydrique à 34 % d'humidité, rien ne lui dit que le minimum retenu est 35, ni
 * que ce minimum a changé en passant en fructification.
 *
 * <p><strong>Pure restitution : aucun calcul nouveau.</strong>
 * {@code CropRequirementResolver} fusionne depuis toujours {@code crop_requirement} avec
 * {@code crop_stage_requirement} (V10, où une colonne nulle signifie « ce stade
 * n'infléchit pas ce seuil »). Seule l'exposition manquait.
 *
 * <h2>L'origine de chaque valeur est dite</h2>
 *
 * <p>{@code origin} distingue un seuil général de la culture d'un seuil propre au stade.
 * C'est ce qui explique qu'un même taux d'humidité déclenche un conseil en floraison et
 * pas en levée — sans quoi le système paraît changer d'avis.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CropThresholds {

    private Long cropId;
    private Long plotId;
    private String plotName;
    private String cropName;

    /** Stade en cours, <strong>recalculé</strong> depuis la date de plantation. */
    private String currentStage;
    private String currentStageLabel;

    /**
     * Tous les stades du cycle, dans l'ordre, avec les seuils applicables à chacun.
     *
     * <p>Le stade courant y figure comme les autres, marqué {@code current: true} : lire
     * les seuils à venir permet d'anticiper, ce qui est l'usage le plus utile de cette
     * vue.
     */
    private List<StageThresholds> stages;

    /**
     * Réserve, <strong>toujours renseignée</strong>.
     *
     * <p>Les valeurs semées sont indicatives et n'ont pas été validées par une source
     * agronomique. Les afficher sans le dire les ferait passer pour des références.
     */
    private String limitation;

    private List<String> missingData;

    private Instant generatedAt;

    /** Les seuils applicables à un stade. */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class StageThresholds {

        private String stage;
        private String stageLabel;

        /** Début projeté de la phase — {@code null} sans date de plantation. */
        private LocalDate startsOn;

        private Boolean current;

        /**
         * Vrai si {@code crop_stage_requirement} porte une ligne pour ce stade.
         *
         * <p>Faux ⇒ les seuils sont exactement ceux de la culture. À afficher : cela
         * évite de chercher une nuance qui n'existe pas.
         */
        private Boolean hasStageOverride;

        private List<ThresholdRange> measures;

        /**
         * Tolérance à la sécheresse, de 0 à 1 : elle atténue la sévérité calculée.
         *
         * <p>Exposée à part parce qu'elle n'est pas une plage mais un coefficient — la
         * ranger parmi les mesures ferait lire un facteur comme un seuil.
         */
        private Double toleranceSecheresse;

        private String toleranceOrigin;
    }

    /**
     * Une plage de tolérance sur une mesure.
     *
     * @param origin {@code GENERALE} (seuil de la culture) ou {@code STADE} (propre à
     *               cette phase). C'est ce qui explique qu'une même valeur déclenche un
     *               conseil à un stade et pas à un autre
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class ThresholdRange {

        /** Clé technique, identique à celle des relevés : {@code humidite_sol}, {@code ph}… */
        private String measure;

        /** Libellé français, prêt à afficher. */
        private String label;

        private String unit;

        /** {@code null} quand la mesure n'a pas de borne basse. */
        private Double min;

        /** {@code null} sur les seuils nutritifs, qui n'ont pas de maximum. */
        private Double max;

        private String origin;
        private String originLabel;

        /** Formulation complète, prête à afficher. */
        private String statement;
    }
}
