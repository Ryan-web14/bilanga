package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.GrowthStage;
import com.sni.bilanga.farm.model.Crop;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Déduit le stade de développement d'une culture depuis sa date de plantation.
 *
 * <p><strong>Le défaut que cela corrige.</strong> {@code growthStage} est une
 * colonne saisie à la main. Personne ne revient la modifier : une tomate plantée
 * en mars reste « LEVEE » jusqu'à la récolte. Or {@code CropRequirementResolver}
 * infléchit les seuils agronomiques selon ce stade, et
 * {@code AgronomicEngine} en tire ses constats. Le système raisonnait donc sur un
 * stade faux — avec exactement la même assurance que sur un stade juste, ce qui
 * est le pire des deux mondes.
 *
 * <p><strong>Pourquoi une proportion du cycle, et non des jours fixes.</strong>
 * Une variété précoce et une variété tardive n'ont pas la même durée de cycle,
 * mais elles traversent les mêmes phases dans les mêmes proportions. Exprimer
 * les bornes en fraction du cycle permet à {@code cycleDurationDays} de tout
 * ajuster d'un coup, sans table de correspondance par variété.
 *
 * <p><strong>Pas d'ordonnanceur.</strong> Le recalcul a lieu au fil des
 * diagnostics, là où le stade est réellement consommé. Un stade recalculé que
 * personne ne lit n'a aucune valeur ; ce qui compte est qu'il soit juste au
 * moment où le moteur s'en sert.
 *
 * <p>⚠️ Les durées de référence sont <strong>indicatives</strong>, au même titre
 * que les seuils semés par la migration V10, et devront être validées par des
 * sources agronomiques congolaises avant exploitation réelle.
 */
@Component
public class GrowthStageResolver {

    /** Durées de cycle de référence, en jours, employées à défaut de valeur saisie. */
    private static final Map<Culture, Integer> DEFAULT_CYCLE_DAYS = Map.of(
            Culture.TOMATE, 120,
            Culture.MANIOC, 330);

    /**
     * Découpage du cycle en phases.
     *
     * @param stage  stade concerné
     * @param endsAt fraction du cycle à laquelle il s'achève, dans {@code ]0, 1]}
     */
    private record Phase(GrowthStage stage, double endsAt) {
    }

    /**
     * Les stades ne sont pas communs aux deux cultures : la tomate fructifie, le
     * manioc tubérise. Ces séquences reprennent exactement celles que la
     * migration V10 a semées dans {@code crop_stage_requirement} — s'en écarter
     * produirait un stade sans seuils associés, donc silencieusement ignoré par
     * {@code CropRequirementResolver}.
     */
    private static final Map<Culture, List<Phase>> PHASES = Map.of(
            Culture.TOMATE, List.of(
                    new Phase(GrowthStage.LEVEE, 0.10),
                    new Phase(GrowthStage.CROISSANCE, 0.40),
                    new Phase(GrowthStage.FLORAISON, 0.60),
                    new Phase(GrowthStage.FRUCTIFICATION, 0.85),
                    new Phase(GrowthStage.MATURATION, 1.00)),
            Culture.MANIOC, List.of(
                    new Phase(GrowthStage.LEVEE, 0.08),
                    new Phase(GrowthStage.CROISSANCE, 0.35),
                    new Phase(GrowthStage.TUBERISATION, 0.75),
                    new Phase(GrowthStage.MATURATION, 1.00)));

    // ============================================================
    // Stade
    // ============================================================

    /**
     * Phases d'une culture, ou {@code null} si la base de connaissance ne la
     * couvre pas.
     *
     * <p><strong>Le garde sur {@code null} n'est pas défensif, il est
     * nécessaire.</strong> {@code PHASES} et {@code DEFAULT_CYCLE_DAYS} sont
     * construites par {@code Map.of(...)}, et les cartes immuables du JDK
     * <em>lèvent</em> une {@code NullPointerException} sur {@code get(null)} et
     * {@code getOrDefault(null, …)} — là où {@code HashMap} rendrait
     * paisiblement {@code null}. Or {@code Culture.from} rend {@code null} pour
     * toute culture hors {tomate, manioc}, et {@code cropName} est une colonne
     * {@code VARCHAR} libre.
     *
     * <p>Conséquence avant ce garde : une culture nommée « maïs » ou « haricot »
     * faisait échouer {@code stageFor} par exception, et non par un {@code null}
     * signifiant « je ne sais pas ». Comme {@code ContextResolver} appelle
     * {@code refreshGrowthStage} à <strong>chaque</strong> diagnostic, la
     * NullPointerException remontait au cœur du pipeline et faisait perdre le
     * diagnostic entier — pour une culture simplement non couverte.
     */
    private static List<Phase> phasesFor(String cropName) {
        Culture culture = Culture.from(cropName);
        return culture == null ? null : PHASES.get(culture);
    }

    /**
     * Stade attendu aujourd'hui, ou {@code null} si le calcul n'est pas fondé —
     * culture inconnue de la base de connaissance, ou date de plantation absente.
     *
     * <p>{@code null} signifie « je ne sais pas », jamais « aucun stade » :
     * l'appelant doit alors conserver la valeur enregistrée plutôt que
     * l'effacer.
     */
    public GrowthStage stageFor(Crop crop) {
        return crop == null ? null : stageFor(crop.getCropName(), crop.getPlantingDate(),
                crop.getCycleDurationDays(), LocalDate.now());
    }

    public GrowthStage stageFor(String cropName, LocalDate plantingDate,
                                Integer cycleDurationDays, LocalDate asOf) {

        List<Phase> phases = phasesFor(cropName);
        if (phases == null || plantingDate == null || asOf == null) {
            return null;
        }

        int cycle = cycleDays(cropName, cycleDurationDays);
        long elapsed = ChronoUnit.DAYS.between(plantingDate, asOf);

        // Une date de plantation à venir décrit une plantation programmée, pas
        // une erreur : le premier stade reste la réponse juste.
        if (elapsed <= 0) {
            return phases.getFirst().stage();
        }

        double progress = (double) elapsed / cycle;

        for (Phase phase : phases) {
            if (progress <= phase.endsAt()) {
                return phase.stage();
            }
        }

        // Cycle dépassé : la culture est mûre, voire en retard de récolte. Le
        // dernier stade reste le plus proche de la réalité — c'est le statut de
        // la culture, non son stade, qui doit alors passer à TERMINEE.
        return phases.getLast().stage();
    }

    /**
     * Vrai lorsque le stade enregistré s'écarte du stade calculé et mérite d'être
     * corrigé. Un stade absent en base est toujours à renseigner.
     */
    public boolean isStale(Crop crop) {
        GrowthStage computed = stageFor(crop);
        if (computed == null) {
            return false;
        }
        return computed != GrowthStage.from(crop.getGrowthStage());
    }

    /**
     * Date à laquelle chaque stade a commencé, du plus ancien au plus récent.
     *
     * <p>Les changements de stade ne sont enregistrés nulle part : le stade est
     * une colonne écrasée, pas un journal. Or c'est une information qui compte
     * pour relire l'histoire d'une parcelle — un diagnostic pris en floraison ne
     * se lit pas comme le même diagnostic pris en maturation. Comme le stade est
     * une fonction déterministe de la date de plantation, ces dates se
     * <strong>reconstituent</strong> sans qu'il ait fallu les stocker.
     *
     * <p>Les stades à venir sont inclus : c'est ce qui permet d'annoncer
     * « floraison attendue dans neuf jours » plutôt que de la constater après
     * coup. L'appelant filtre selon ce qu'il veut montrer.
     *
     * @param asOf sans effet sur le calcul ; sert à l'appelant pour distinguer
     *             le passé de l'à-venir
     */
    public List<StageStart> stageTimeline(Crop crop) {
        if (crop == null || crop.getPlantingDate() == null) {
            return List.of();
        }

        List<Phase> phases = phasesFor(crop.getCropName());
        if (phases == null) {
            return List.of();
        }

        int cycle = cycleDays(crop.getCropName(), crop.getCycleDurationDays());
        List<StageStart> starts = new ArrayList<>();

        // Le premier stade commence à la plantation ; chaque suivant commence le
        // LENDEMAIN du jour où le précédent s'achève.
        //
        // Le « +1 » et le plancher — et non un arrondi — alignent cette méthode
        // sur stageFor, qui retient le premier stade tel que
        // « progression <= fin de phase ». Le jour de la borne appartient donc au
        // stade qui SE TERMINE : sur une tomate à 120 jours, J+12 est encore en
        // levée et la croissance commence J+13.
        //
        // Avant cette correction, l'offset valait round(finPrécédente × cycle),
        // soit J+12 : la chronologie de la parcelle datait chaque changement de
        // stade un jour AVANT celui que le moteur agronomique avait réellement
        // employé. Deux vues du même fait se contredisaient d'un jour, et rien
        // dans le code ne le disait — c'est le genre d'écart qu'on ne trouve
        // qu'en confrontant les deux méthodes.
        double previousEnd = 0d;
        boolean first = true;
        for (Phase phase : phases) {
            long offset = first ? 0 : (long) Math.floor(previousEnd * cycle) + 1;
            starts.add(new StageStart(phase.stage(), crop.getPlantingDate().plusDays(offset)));
            previousEnd = phase.endsAt();
            first = false;
        }
        return starts;
    }

    /**
     * @param stage    stade concerné
     * @param startsOn date de début, reconstituée depuis la plantation
     */
    public record StageStart(GrowthStage stage, LocalDate startsOn) {
    }

    // ============================================================
    // Cycle et récolte
    // ============================================================

    /** Durée retenue : celle de la culture, ou la référence de l'espèce. */
    public int cycleDays(String cropName, Integer declared) {
        if (declared != null && declared > 0) {
            return declared;
        }
        // Même piège que dans phasesFor : getOrDefault(null, …) lève sur une
        // carte immuable du JDK, au lieu de rendre la valeur par défaut.
        Culture culture = Culture.from(cropName);
        return culture == null ? 120 : DEFAULT_CYCLE_DAYS.getOrDefault(culture, 120);
    }

    /**
     * Récolte attendue, calculée quand elle n'a pas été saisie.
     *
     * Une date manquante n'est pas une information : elle empêche le « J-18
     * avant récolte » qui donne son utilité au suivi.
     */
    public LocalDate expectedHarvestDate(Crop crop) {
        if (crop == null || crop.getPlantingDate() == null) {
            return crop == null ? null : crop.getExpectedHarvestDate();
        }
        if (crop.getExpectedHarvestDate() != null) {
            return crop.getExpectedHarvestDate();
        }
        return crop.getPlantingDate()
                .plusDays(cycleDays(crop.getCropName(), crop.getCycleDurationDays()));
    }

    /**
     * Jours restants avant la récolte attendue. Négatif si le terme est passé —
     * c'est précisément le cas qui mérite d'être vu.
     */
    public Integer daysToHarvest(Crop crop) {
        LocalDate expected = expectedHarvestDate(crop);
        return expected == null
                ? null
                : (int) ChronoUnit.DAYS.between(LocalDate.now(), expected);
    }
}
