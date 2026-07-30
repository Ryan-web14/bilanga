package com.sni.bilanga.iot.service.support;

import com.sni.bilanga.iot.model.SensorReading;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrôle de plausibilité <em>matérielle</em> d'un relevé.
 *
 * À ne pas confondre avec la défavorabilité agronomique, qui relève des moteurs
 * de connaissance : ici on ne juge pas si la mesure est mauvaise pour la plante,
 * on juge si elle est physiquement possible. Un pH de 22 n'est pas un sol acide,
 * c'est une sonde en panne.
 *
 * Le relevé reste enregistré dans tous les cas — c'est justement la trace de la
 * panne. Ce contrôle existait en double, à l'identique, dans
 * {@code IngestServiceImpl} et {@code SensorReadingServiceImpl} : deux copies
 * qui auraient fini par diverger.
 */
@Component
public class PlausibilityChecker {

    /**
     * Verdict détaillé. Nommer les mesures en cause permet à l'exploitant de
     * savoir quelle sonde changer, là où un simple booléen le laissait deviner.
     *
     * @param implausible vrai si au moins une mesure est hors du physiquement possible
     * @param offendingFields mesures fautives, dans l'ordre du relevé
     */
    public record Verdict(boolean implausible, List<String> offendingFields) {

        public static final Verdict SOUND = new Verdict(false, List.of());

        /** Formulation destinée à l'exploitant, ou {@code null} si rien à signaler. */
        public String statement() {
            if (!implausible) {
                return null;
            }
            return offendingFields.size() == 1
                    ? "Mesure hors des valeurs physiquement possibles : "
                      + offendingFields.getFirst() + ". Sonde vraisemblablement défaillante."
                    : "Mesures hors des valeurs physiquement possibles : "
                      + String.join(", ", offendingFields) + ". Boîtier à vérifier.";
        }
    }

    public Verdict check(SensorReading reading) {
        List<String> offenders = new ArrayList<>();

        addIfOutside(offenders, "pH", reading.getPh(), 0, 14);
        addIfOutside(offenders, "humidité du sol", reading.getHumiditeSol(), 0, 100);
        addIfOutside(offenders, "humidité de l'air", reading.getHumiditeAir(), 0, 100);
        addIfOutside(offenders, "température de l'air", reading.getTemperature(), -20, 70);

        // Le sol tamponne les extrêmes de l'air : une sonde enterrée qui affiche
        // 65 °C n'a pas relevé une canicule, elle est hors service. La borne est
        // donc plus étroite que celle de l'air, et c'est délibéré.
        addIfOutside(offenders, "température du sol", reading.getTemperatureSol(), -10, 60);

        addIfNegative(offenders, "azote", reading.getAzote());
        addIfNegative(offenders, "phosphore", reading.getPhosphore());
        addIfNegative(offenders, "potassium", reading.getPotassium());
        addIfNegative(offenders, "luminosité", reading.getLuminosite());
        addIfNegative(offenders, "conductivité électrique", reading.getConductiviteElectrique());

        // Une pluviométrie négative n'existe pas ; 500 mm entre deux relevés
        // dépasse ce qu'un épisode tropical peut produire, même violent.
        addIfOutside(offenders, "pluviométrie", reading.getPluviometrie(), 0, 500);

        return offenders.isEmpty() ? Verdict.SOUND : new Verdict(true, List.copyOf(offenders));
    }

    private void addIfOutside(List<String> offenders, String label, Double value, double min, double max) {
        if (value != null && (value < min || value > max)) {
            offenders.add(label);
        }
    }

    private void addIfNegative(List<String> offenders, String label, Double value) {
        if (value != null && value < 0) {
            offenders.add(label);
        }
    }
}
