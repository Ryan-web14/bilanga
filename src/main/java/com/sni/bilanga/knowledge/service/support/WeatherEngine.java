package com.sni.bilanga.knowledge.service.support;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.enums.RecommendationType;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.weather.model.WeatherForecast;
import com.sni.bilanga.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sixième moteur : ce que le ciel annonce.
 *
 * <p><strong>Ce qu'il change.</strong> Les cinq autres moteurs raisonnent sur le
 * passé mesuré — {@code TrendAnalyzer} compris, qui extrapole les mesures
 * internes sans rien savoir du temps qu'il fera. Cela produit les conseils les
 * plus décrédibilisants du système :
 *
 * <ul>
 *   <li>« Humidité du sol à 24 %, irriguez sans délai » — alors que 18 mm de
 *       pluie tombent dans six heures. L'exploitant se déplace, arrose, et la
 *       parcelle est saturée le soir.</li>
 *   <li>« Traitez contre le mildiou » — deux heures avant une averse qui
 *       lessivera le produit. Le traitement est perdu, et son échec sera mis au
 *       compte du produit.</li>
 *   <li>Le risque de maladie est calculé sur l'humidité mesurée, alors que trois
 *       jours d'humidité annoncée au-dessus de 85 % justifieraient une alerte
 *       <em>avant</em> l'apparition des symptômes.</li>
 * </ul>
 *
 * <p><strong>Aligné sur les cinq autres.</strong> Mêmes {@code RecommendationItem},
 * mêmes catégories — ce qui permet à {@code ConflictArbitrator} de concilier
 * {@code STRESS_HYDRIQUE} et {@code PLUIE_ANNONCEE} sans traitement particulier —
 * et même traçabilité {@code measureField}/{@code observedValue}/{@code thresholdValue},
 * pour que {@code DiagnosisExplainer} sache justifier ces conseils comme les
 * autres.
 *
 * <p><strong>Le silence est prévu.</strong> Liste vide si la météo est
 * désactivée, si la parcelle n'a pas de coordonnées, ou si le fournisseur ne
 * répond pas. Le système reste utilisable sans météo — c'est la règle appliquée
 * au microservice d'inférence, et elle vaut ici.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherEngine {

    private static final Locale FR = Locale.FRANCE;

    /** Catégorie reconnue par l'arbitrage face à {@code STRESS_HYDRIQUE}. */
    public static final String CATEGORY_RAIN_EXPECTED = "PLUIE_ANNONCEE";
    public static final String CATEGORY_DISEASE_RISK = "RISQUE_MALADIE";

    /** Heures consécutives d'humidité élevée annoncées qui justifient une alerte préventive. */
    private static final int HUMID_HOURS_FOR_ALERT = 12;

    private final WeatherService weatherService;
    private final BilangaProperties.Weather config;

    public List<RecommendationItem> evaluate(Plot plot) {
        List<RecommendationItem> items = new ArrayList<>();

        List<WeatherForecast> forecasts = weatherService.forecastFor(plot);
        if (forecasts.isEmpty()) {
            return items;
        }

        addRainAdvisory(items, forecasts);
        addTreatmentWindow(items, forecasts);
        addProjectedDiseaseRisk(items, forecasts);

        return items;
    }

    // ============================================================
    // Pluie annoncée — différer l'irrigation
    // ============================================================

    /**
     * Ce conseil ne supprime pas le constat de déficit hydrique : le sol manque
     * bel et bien d'eau. Il ajoute l'information qui manquait pour décider
     * <em>quand</em> agir. C'est {@code ConflictArbitrator} qui réunit les deux,
     * conformément à sa vocation — ajouter une synthèse, jamais retirer un
     * conseil.
     */
    private void addRainAdvisory(List<RecommendationItem> items, List<WeatherForecast> forecasts) {
        Instant horizon = Instant.now().plus(Duration.ofHours(config.getTreatmentRainWindowHours() * 2));

        double cumulative = 0d;
        Instant firstRain = null;

        for (WeatherForecast forecast : forecasts) {
            if (forecast.getForecastAt().isAfter(horizon) || forecast.getPrecipitationMm() == null) {
                continue;
            }
            if (forecast.getPrecipitationMm() > 0 && firstRain == null) {
                firstRain = forecast.getForecastAt();
            }
            cumulative += forecast.getPrecipitationMm();
        }

        if (cumulative < config.getRainThresholdMm() || firstRain == null) {
            return;
        }

        long hoursAway = Math.max(0, Duration.between(Instant.now(), firstRain).toHours());

        items.add(RecommendationItem.builder()
                .content(String.format(FR,
                        "%.0f mm de pluie sont attendus d'ici %d h (première précipitation dans "
                                + "environ %d h). Si un arrosage était prévu, différez-le : le sol "
                                + "sera réalimenté sans effort ni coût, et irriguer maintenant "
                                + "risquerait de le saturer.",
                        cumulative, config.getTreatmentRainWindowHours() * 2, hoursAway))
                .type(RecommendationType.METEO.name())
                // MOYENNE : différer une action n'est pas urgent en soi. La
                // classer HAUTE la placerait devant des conseils qui, eux,
                // appellent un déplacement.
                .priority("MOYENNE")
                .category(CATEGORY_RAIN_EXPECTED)
                .measureField("pluviometrie")
                .observedValue(round(cumulative))
                .thresholdValue(config.getRainThresholdMm())
                .build());
    }

    // ============================================================
    // Fenêtre de traitement
    // ============================================================

    /**
     * Un produit appliqué juste avant une averse est lessivé avant d'agir.
     *
     * <p>Le coût de ce conseil manquant est double : le produit est perdu, et son
     * inefficacité apparente sera imputée au produit ou au système plutôt qu'au
     * moment choisi.
     */
    private void addTreatmentWindow(List<RecommendationItem> items, List<WeatherForecast> forecasts) {
        Instant limit = Instant.now().plus(Duration.ofHours(config.getTreatmentRainWindowHours()));

        boolean rainSoon = forecasts.stream()
                .filter(f -> !f.getForecastAt().isAfter(limit))
                .anyMatch(f -> f.getPrecipitationMm() != null && f.getPrecipitationMm() >= 1);

        if (!rainSoon) {
            return;
        }

        items.add(RecommendationItem.builder()
                .content(String.format(FR,
                        "De la pluie est annoncée dans les %d h : n'appliquez aucun traitement "
                                + "foliaire d'ici là, il serait lessivé avant d'agir. Attendez la "
                                + "fin de l'épisode et un feuillage ressuyé.",
                        config.getTreatmentRainWindowHours()))
                .type(RecommendationType.METEO.name())
                .priority("HAUTE")
                .category(CATEGORY_RAIN_EXPECTED)
                .build());
    }

    // ============================================================
    // Risque projeté
    // ============================================================

    /**
     * Alerte préventive fondée sur l'humidité <em>annoncée</em>.
     *
     * <p>{@code RiskEngine} évalue les conditions d'apparition sur la mesure du
     * moment. Or une maladie foliaire s'installe après plusieurs heures
     * d'humidité élevée : quand la mesure la constate, l'infection a déjà
     * commencé. La prévision permet d'agir avant, ce qui est le seul moment où
     * un traitement préventif a du sens.
     */
    private void addProjectedDiseaseRisk(List<RecommendationItem> items,
                                         List<WeatherForecast> forecasts) {

        long humidHours = forecasts.stream()
                .filter(f -> f.getHumidite() != null)
                .filter(f -> f.getHumidite() >= config.getHighHumidityThreshold())
                .count();

        if (humidHours < HUMID_HOURS_FOR_ALERT) {
            return;
        }

        items.add(RecommendationItem.builder()
                .content(String.format(FR,
                        "Humidité de l'air annoncée au-dessus de %.0f %% pendant %d h sur les "
                                + "prochaines %d h. Ces conditions favorisent les maladies "
                                + "foliaires. Un traitement préventif, une aération du feuillage "
                                + "ou un espacement des arrosages sont à envisager MAINTENANT : "
                                + "une fois les taches visibles, l'infection est installée.",
                        config.getHighHumidityThreshold(), humidHours, config.getHorizonHours()))
                .type(RecommendationType.METEO.name())
                .priority("HAUTE")
                // Même catégorie que RiskEngine : l'arbitrage et la déduplication
                // les traitent alors comme relevant du même domaine.
                .category(CATEGORY_DISEASE_RISK)
                .measureField("humidite_air")
                .observedValue((double) humidHours)
                .thresholdValue((double) HUMID_HOURS_FOR_ALERT)
                .build());
    }

    private double round(double value) {
        return Math.round(value * 10d) / 10d;
    }
}
