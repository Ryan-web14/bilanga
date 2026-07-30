package com.sni.bilanga.farm.service.support;

import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.exception.customs.BusinessRuleException;
import com.sni.bilanga.farm.dto.request.CropRequest;
import com.sni.bilanga.farm.model.Crop;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Applique une requête de mise à jour sur un cycle, <strong>sans effacer ce qui n'a
 * pas été demandé</strong>.
 *
 * <h2>Le défaut corrigé</h2>
 *
 * <p>{@code CropServiceImpl.update} écrivait inconditionnellement :
 *
 * <pre>
 * crop.setVariety(request.getVariety());
 * crop.setSeedLot(request.getSeedLot());
 * crop.setPlantedArea(request.getPlantedArea());
 * </pre>
 *
 * <p>Un client qui n'envoyait que la variété effaçait donc la surface plantée, la
 * densité, le lot de semence et la date de plantation. Silencieusement : aucune erreur,
 * aucune trace.
 *
 * <p><strong>Et la conséquence n'était pas cosmétique.</strong> {@code plantedArea}
 * conditionne {@code yieldPerHectare} et {@code marginPerHectare} — les deux seuls
 * chiffres comparables entre parcelles. Une mise à jour partielle rendait donc la
 * campagne incomparable, et le bilan économique affichait {@code null} sans que rien
 * n'explique pourquoi. Le défaut se manifestait à des semaines de sa cause.
 *
 * <h2>Les deux règles</h2>
 *
 * <p><strong>1. Un champ {@code null} n'est pas touché.</strong> C'est la sémantique
 * que tout le monde attendait de cette route, et celle que le nom « mise à jour »
 * promet.
 *
 * <p><strong>2. Effacer se demande explicitement</strong>, par {@code clearFields}.
 * En JSON, un champ absent et un champ {@code null} sont indiscernables : sans ce
 * second mécanisme, la règle n°1 rendrait tout effacement impossible — on aurait
 * troqué une perte de données silencieuse contre une donnée indélébile.
 *
 * <h2>Ce qui ne change pas</h2>
 *
 * <p>Un client qui envoie l'objet <strong>complet</strong> — le cas du formulaire de
 * Rolle — obtient exactement le même résultat qu'avant : tous les champs sont
 * renseignés, donc tous s'appliquent. La correction ne casse que le comportement sur
 * lequel personne ne pouvait raisonnablement compter.
 *
 * <p>Sans état ni transaction : la fusion se teste sans base.
 */
@Component
public class CropUpdateMerger {

    /**
     * Champs effaçables, et l'action qui les vide.
     *
     * <p>{@code cropName}, {@code plotId} et {@code status} n'y figurent pas : les deux
     * premiers sont obligatoires, le troisième relève du cycle de vie et se pilote par
     * la clôture — le vider laisserait un cycle sans état.
     */
    private static final java.util.Map<String, BiConsumer<Crop, Void>> CLEARABLE =
            java.util.Map.of(
                    "variety", (crop, ignored) -> crop.setVariety(null),
                    "plantingDate", (crop, ignored) -> crop.setPlantingDate(null),
                    "cycleDurationDays", (crop, ignored) -> crop.setCycleDurationDays(null),
                    "expectedHarvestDate", (crop, ignored) -> crop.setExpectedHarvestDate(null),
                    "plantedArea", (crop, ignored) -> crop.setPlantedArea(null),
                    "plantDensity", (crop, ignored) -> crop.setPlantDensity(null),
                    "seedLot", (crop, ignored) -> crop.setSeedLot(null),
                    "growthStage", (crop, ignored) -> crop.setGrowthStage(null));

    /**
     * Applique la requête sur le cycle.
     *
     * <p>L'ordre compte : on applique <strong>puis</strong> on efface. Un champ à la
     * fois renseigné et listé dans {@code clearFields} finit donc vidé — l'intention
     * explicite l'emporte sur la valeur, et c'est le seul ordre qui rende
     * {@code clearFields} utile plutôt que contradictoire.
     *
     * <p>Ne touche ni au statut ni à la parcelle : ces deux-là portent des règles
     * métier propres ({@code requireSingleActiveCrop}) et restent gérés par le service.
     */
    public void apply(Crop crop, CropRequest request) {
        if (crop == null || request == null) {
            return;
        }

        // cropName est @NotNull dans la requête : il est toujours présent, et
        // l'appliquer inconditionnellement ne peut rien effacer.
        if (request.getCropName() != null) {
            crop.setCropName(request.getCropName().storageName());
        }

        applyIfPresent(request.getVariety(), crop::setVariety);
        applyIfPresent(request.getPlantingDate(), crop::setPlantingDate);
        applyIfPresent(request.getCycleDurationDays(), crop::setCycleDurationDays);
        applyIfPresent(request.getExpectedHarvestDate(), crop::setExpectedHarvestDate);
        applyIfPresent(request.getPlantedArea(), crop::setPlantedArea);
        applyIfPresent(request.getPlantDensity(), crop::setPlantDensity);
        applyIfPresent(request.getSeedLot(), crop::setSeedLot);

        if (request.getGrowthStage() != null) {
            crop.setGrowthStage(DomainEnums.nameOf(request.getGrowthStage()));
        }

        applyClears(crop, request.getClearFields());
    }

    private <T> void applyIfPresent(T value, java.util.function.Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /**
     * Vide les champs explicitement demandés.
     *
     * <p><strong>Un nom inconnu est refusé, jamais ignoré.</strong> Ignorer produirait
     * exactement le défaut qu'on vient de corriger, en sens inverse : un effacement qui
     * n'a pas lieu et ne le dit pas. L'appelant croirait la donnée supprimée et
     * découvrirait le contraire des semaines plus tard.
     *
     * <p>La comparaison est insensible à la casse, comme le reste du vocabulaire de
     * l'API : exiger la casse exacte transformerait une différence de présentation en
     * erreur 400.
     */
    private void applyClears(Crop crop, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return;
        }

        Set<String> unknown = new LinkedHashSet<>();

        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String field = canonicalOf(raw.trim());

            if (field == null) {
                unknown.add(raw.trim());
                continue;
            }
            CLEARABLE.get(field).accept(crop, null);
        }

        if (!unknown.isEmpty()) {
            throw new BusinessRuleException(String.format(
                    "Champ(s) inconnu(s) dans clearFields : %s. Champs effaçables : %s.",
                    String.join(", ", unknown),
                    String.join(", ", new java.util.TreeSet<>(CLEARABLE.keySet()))));
        }
    }

    /** Nom canonique du champ, insensible à la casse ; {@code null} si inconnu. */
    private String canonicalOf(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        return CLEARABLE.keySet().stream()
                .filter(known -> known.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** Les champs effaçables, pour la documentation et les messages d'erreur. */
    public Set<String> clearableFields() {
        return new java.util.TreeSet<>(CLEARABLE.keySet());
    }
}
