package com.sni.bilanga.iot.dto.request;


import com.sni.bilanga.enums.ReadingQuality;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Saisie d'un relevé par un humain, par opposition à
 * {@link IngestReadingRequest} qui vient d'un boîtier.
 *
 * <p>Les bornes sont ici <strong>strictes</strong>, et c'est délibérément
 * l'inverse de l'ingestion machine : un pH de 22 saisi à la main est une faute
 * de frappe, qu'il faut refuser tout de suite ; le même pH remonté par une sonde
 * est le symptôme d'une panne, qu'il faut enregistrer pour pouvoir la constater.
 */
@Data
public class SensorReadingRequest {

    @NotNull(message = "La parcelle est obligatoire")
    private Long plotId;

    private Long deviceId;

    /** Température de l'air, en °C. */
    private Double temperature;

    /** Température du sol, en °C. */
    private Double temperatureSol;

    @DecimalMin(value = "0", message = "L'humidité du sol est un pourcentage")
    @DecimalMax(value = "100", message = "L'humidité du sol est un pourcentage")
    private Double humiditeSol;

    @DecimalMin(value = "0", message = "L'humidité de l'air est un pourcentage")
    @DecimalMax(value = "100", message = "L'humidité de l'air est un pourcentage")
    private Double humiditeAir;

    @DecimalMin(value = "0", message = "Le pH est compris entre 0 et 14")
    @DecimalMax(value = "14", message = "Le pH est compris entre 0 et 14")
    private Double ph;

    private Double azote;
    private Double phosphore;
    private Double potassium;
    private Double luminosite;

    @DecimalMin(value = "0", message = "La pluviométrie ne peut être négative")
    private Double pluviometrie;

    @DecimalMin(value = "0", message = "La conductivité électrique ne peut être négative")
    private Double conductiviteElectrique;

    /** Provenance. Par défaut {@code MANUELLE} pour une saisie via cette route. */
    private ReadingQuality quality;
}