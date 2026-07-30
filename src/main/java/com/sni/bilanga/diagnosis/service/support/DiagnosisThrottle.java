package com.sni.bilanga.diagnosis.service.support;


import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.repository.DiagnosticRepository;
import com.sni.bilanga.iot.model.SensorReading;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Décide s'il y a lieu de produire un nouveau diagnostic.
 *
 * Un boîtier émettant toutes les trente secondes produirait cent vingt
 * diagnostics par heure, tous identiques tant que rien ne change sur la
 * parcelle. L'historique deviendrait illisible et les recommandations
 * s'accumuleraient par centaines sans apporter d'information.
 *
 * Deux conditions déclenchent un nouveau diagnostic : un délai écoulé depuis
 * le précédent, ou une mesure qui a bougé de façon significative. Les relevés,
 * eux, sont toujours enregistrés : c'est la donnée brute, on ne la jette pas.
 */
@Component
@RequiredArgsConstructor
public class DiagnosisThrottle {

    private static final Locale FR = Locale.FRANCE;

    private final DiagnosticRepository diagnosticRepository;
    private final BilangaProperties.Diagnosis diagnosisConfig;







    /**
     * @return le motif du renoncement, ou vide s'il faut diagnostiquer.
     */
    public Optional<String> reasonToSkip(Long plotId, SensorReading current) {
        Optional<Diagnostic> previous =
                diagnosticRepository.findFirstByPlot_IdOrderByDiagnosedAtDesc(plotId);

        if (previous.isEmpty()) {
            return Optional.empty(); // première mesure de la parcelle
        }

        Diagnostic last = previous.get();

        long elapsed = Duration.between(last.getDiagnosedAt(), Instant.now()).toMinutes();
        if (elapsed >= diagnosisConfig.getMinIntervalMinutes()) {
            return Optional.empty();
        }

        SensorReading reference = last.getReading();
        if (reference == null) {
            return Optional.empty(); // rien à quoi comparer
        }

        String changed = firstSignificantChange(reference, current);
        if (changed != null) {
            return Optional.empty();
        }

        return Optional.of(String.format(FR,
                "Relevé conservé. Aucun diagnostic relancé : les conditions sont stables "
                        + "depuis le dernier diagnostic, il y a %d minute%s.",
                elapsed, elapsed > 1 ? "s" : ""));
    }

    /** Premier champ dont l'écart dépasse son seuil, ou null si tout est stable. */
    private String firstSignificantChange(SensorReading before, SensorReading after) {
        if (moved(before.getHumiditeSol(), after.getHumiditeSol(), diagnosisConfig.getThreshold().getHumidite())) return "humidite_sol";
        if (moved(before.getHumiditeAir(), after.getHumiditeAir(), diagnosisConfig.getThreshold().getHumidite())) return "humidite_air";
        if (moved(before.getTemperature(), after.getTemperature(), diagnosisConfig.getThreshold().getTemperature())) return "temperature";
        if (moved(before.getPh(), after.getPh(), diagnosisConfig.getThreshold().getPh())) return "ph";
        if (moved(before.getAzote(), after.getAzote(), diagnosisConfig.getThreshold().getNutriment())) return "azote";
        if (moved(before.getPhosphore(), after.getPhosphore(), diagnosisConfig.getThreshold().getNutriment())) return "phosphore";
        if (moved(before.getPotassium(), after.getPotassium(), diagnosisConfig.getThreshold().getNutriment())) return "potassium";
        if (moved(before.getLuminosite(), after.getLuminosite(), diagnosisConfig.getThreshold().getLuminosite())) return "luminosite";
        return null;
    }

    /**
     * Une valeur qui apparaît ou disparaît constitue un changement : passer
     * d'une sonde muette à une sonde qui répond est une information.
     */
    private boolean moved(Double before, Double after, double threshold) {
        if (before == null && after == null) return false;
        if (before == null || after == null) return true;
        return Math.abs(after - before) >= threshold;
    }
}
