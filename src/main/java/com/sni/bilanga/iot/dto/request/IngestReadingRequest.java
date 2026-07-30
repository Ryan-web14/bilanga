package com.sni.bilanga.iot.dto.request;


import com.sni.bilanga.enums.ReadingQuality;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

/**
 * Relevé transmis par un boîtier de terrain.
 *
 * Le matériel s'identifie par son numéro de série et ignore tout de
 * l'organisation logique de l'exploitation : c'est le serveur qui résout la
 * parcelle. Un boîtier déplacé d'une parcelle à l'autre n'a ainsi pas besoin
 * d'être reprogrammé.
 *
 * <p><strong>Les bornes ci-dessous sont volontairement très larges.</strong>
 * Elles n'écartent que l'absurde — ce qui ne peut venir que d'une trame corrompue.
 * Une valeur simplement impossible physiquement (pH 22, humidité 130 %) doit être
 * <em>enregistrée</em> et marquée {@code anomalyDetected}, jamais rejetée : c'est
 * ainsi qu'on détecte une sonde qui dérive. Confondre les deux ferait disparaître
 * la panne au lieu de la signaler.
 */
@Data
public class IngestReadingRequest {

    @NotBlank(message = "L'identifiant du boîtier est obligatoire")
    @Size(max = 100, message = "L'identifiant du boîtier ne peut dépasser 100 caractères")
    private String technicalId;

    /** Température de l'<strong>air</strong>, en °C — celle que les moteurs comparent aux seuils. */
    @DecimalMin(value = "-273.15", message = "Température en deçà du zéro absolu")
    @DecimalMax(value = "1000", message = "Température hors de toute plage exploitable")
    private Double temperature;

    /** Température du <strong>sol</strong>, en °C. Commande germination et tubérisation. */
    @DecimalMin(value = "-273.15", message = "Température du sol en deçà du zéro absolu")
    @DecimalMax(value = "1000", message = "Température du sol hors de toute plage exploitable")
    private Double temperatureSol;

    @DecimalMin(value = "-500") @DecimalMax(value = "1000")
    private Double humiditeSol;

    @DecimalMin(value = "-500") @DecimalMax(value = "1000")
    private Double humiditeAir;

    @DecimalMin(value = "-50") @DecimalMax(value = "100")
    private Double ph;

    @DecimalMin(value = "-1000") @DecimalMax(value = "100000")
    private Double azote;

    @DecimalMin(value = "-1000") @DecimalMax(value = "100000")
    private Double phosphore;

    @DecimalMin(value = "-1000") @DecimalMax(value = "100000")
    private Double potassium;

    @DecimalMin(value = "-1000") @DecimalMax(value = "1000000")
    private Double luminosite;

    /** Pluie tombée depuis le relevé précédent, en mm. */
    @DecimalMin(value = "-100") @DecimalMax(value = "10000")
    private Double pluviometrie;

    /** Conductivité électrique du sol : salinité et charge en engrais. */
    @DecimalMin(value = "-1000") @DecimalMax(value = "100000")
    private Double conductiviteElectrique;

    /**
     * Puissance du signal reçu, en dBm — donc négative.
     *
     * Ce n'est pas une mesure agronomique : elle sert à distinguer une sonde en
     * panne d'une couverture réseau insuffisante, deux situations qui se
     * traduisent l'une comme l'autre par des relevés manquants.
     */
    @Min(value = -150, message = "La puissance du signal se mesure en dBm (valeur négative)")
    @Max(value = 0, message = "La puissance du signal se mesure en dBm (valeur négative)")
    private Integer signalStrength;

    /** Niveau de charge du boîtier, transmis avec le relevé. */
    @Min(value = 0, message = "Le niveau de batterie ne peut être négatif")
    @Max(value = 100, message = "Le niveau de batterie ne peut dépasser 100 %")
    private Integer batteryLevel;

    /** Tension brute de la batterie, en volts. Vieillit mieux que le pourcentage. */
    @DecimalMin(value = "0", message = "La tension de batterie ne peut être négative")
    @DecimalMax(value = "30", message = "La tension de batterie ne peut dépasser 30 V")
    private Double batteryVoltage;

    /** Version du micrologiciel, remontée par le boîtier lui-même. */
    @Size(max = 40, message = "La version du micrologiciel ne peut dépasser 40 caractères")
    private String firmwareVersion;

    /**
     * Horodatage de la mesure, si le boîtier le connaît.
     *
     * Sans ce champ, un boîtier qui tamponne ses relevés pendant une coupure
     * réseau ne pouvait rien rattraper : tout ce qu'il émettait au retour
     * portait l'heure de réception, ce qui écrasait la chronologie réelle et
     * faussait l'analyse de tendance. Absent, l'heure de réception s'applique.
     */
    @PastOrPresent(message = "L'horodatage du relevé ne peut être dans le futur")
    private Instant recordedAt;

    /** Provenance du relevé. Par défaut {@code TERRAIN}. */
    private ReadingQuality quality;
}
