package com.sni.bilanga.farm.dto.request;


import com.sni.bilanga.enums.CropStatus;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.GrowthStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CropRequest {

    @NotNull(message = "La parcelle est obligatoire")
    private Long plotId;

    /**
     * Typée : seules les cultures couvertes par la base de connaissance sont
     * acceptées. En chaîne libre, une valeur comme « tomat » était enregistrée
     * puis produisait un diagnostic vide, sans que rien ne signale la faute.
     */
    @NotNull(message = "La culture est obligatoire")
    private Culture cropName;

    @Size(max = 100, message = "La variété ne peut dépasser 100 caractères")
    private String variety;

    @PastOrPresent(message = "La date de plantation ne peut être dans le futur")
    private LocalDate plantingDate;

    /**
     * Durée du cycle, en jours.
     *
     * Facultative : à défaut, la durée de référence de la culture s'applique.
     * La renseigner sert surtout aux variétés précoces ou tardives, dont le
     * cycle s'écarte de la moyenne.
     */
    @Positive(message = "La durée du cycle doit être positive")
    @Max(value = 1000, message = "La durée du cycle ne peut dépasser 1000 jours")
    private Integer cycleDurationDays;

    /**
     * Récolte attendue. Laissée vide, elle est <strong>calculée</strong> depuis
     * la date de plantation et la durée du cycle.
     */
    private LocalDate expectedHarvestDate;

    @Positive(message = "La surface plantée doit être positive")
    private Double plantedArea;

    @Positive(message = "La densité de plantation doit être positive")
    private Integer plantDensity;

    @Size(max = 60, message = "Le lot de semence ne peut dépasser 60 caractères")
    private String seedLot;

    /**
     * Stade de développement.
     *
     * Facultatif, et rarement utile à renseigner : il est <strong>recalculé</strong>
     * depuis la date de plantation à chaque diagnostic. Une valeur fournie ici
     * ne sert que d'amorce, ou de correction ponctuelle quand la levée a été
     * plus lente que prévu.
     */
    private GrowthStage growthStage;

    private CropStatus status;

    /**
     * Champs à <strong>effacer explicitement</strong>, par leur nom.
     *
     * <h2>Le défaut corrigé</h2>
     *
     * <p>La mise à jour écrasait <em>inconditionnellement</em> tous les champs de cette
     * requête. Un {@code PUT} partiel — un client qui n'envoie que la variété — effaçait
     * donc silencieusement la surface plantée, la densité, le lot de semence et la date
     * de plantation. Aucune erreur, aucune trace : la donnée disparaissait.
     *
     * <p>La conséquence n'était pas cosmétique. {@code plantedArea} conditionne
     * {@code yieldPerHectare} et {@code marginPerHectare} — les deux seuls chiffres
     * comparables entre parcelles. Un {@code PUT} partiel rendait donc la campagne
     * incomparable, sans que personne ne sache pourquoi.
     *
     * <h2>Pourquoi ce champ existe</h2>
     *
     * <p>La mise à jour est désormais <strong>partielle</strong> : un champ absent ou
     * {@code null} n'est pas touché. Mais en JSON, « absent » et « null » sont
     * indiscernables — sans quoi il deviendrait impossible d'effacer une variété saisie
     * par erreur.
     *
     * <p>D'où cette liste : elle rend l'effacement <strong>explicite et
     * intentionnel</strong>, ce qu'un {@code null} ambigu ne pouvait pas être.
     *
     * <pre>
     * { "cropName": "TOMATE", "plotId": "42", "clearFields": ["variety", "seedLot"] }
     * </pre>
     *
     * <p>Noms acceptés : {@code variety}, {@code plantingDate}, {@code cycleDurationDays},
     * {@code expectedHarvestDate}, {@code plantedArea}, {@code plantDensity},
     * {@code seedLot}, {@code growthStage}. Un nom inconnu est refusé en 400 plutôt
     * qu'ignoré — un effacement qui n'a pas lieu et ne le dit pas serait le défaut
     * qu'on vient de corriger, en sens inverse.
     *
     * <p>⚠️ {@code cycleDurationDays}, {@code expectedHarvestDate} et
     * {@code growthStage} sont <strong>redérivés</strong> après effacement par
     * {@code deriveCycle} : les vider revient à demander leur recalcul, non à les
     * laisser vides. C'est d'ailleurs l'usage principal — forcer un recalcul après
     * correction de la date de plantation.
     *
     * <p>Sans effet à la création.
     */
    private java.util.List<String> clearFields;
}
