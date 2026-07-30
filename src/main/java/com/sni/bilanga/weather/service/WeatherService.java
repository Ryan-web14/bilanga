package com.sni.bilanga.weather.service;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.weather.client.dto.response.HourlyForecast;
import com.sni.bilanga.weather.client.interfaces.WeatherClient;
import com.sni.bilanga.weather.model.WeatherForecast;
import com.sni.bilanga.weather.repository.WeatherForecastRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Prévisions d'une parcelle, mises en cache.
 *
 * <p><strong>Aucun ordonnanceur.</strong> Le projet n'en a pas, et il n'en faut
 * pas ici : le rafraîchissement est déclenché par le diagnostic qui a besoin de
 * la prévision. Rafraîchir périodiquement les quarante parcelles consommerait le
 * quota du fournisseur pour des données que personne ne lira.
 *
 * <p><strong>Le silence est une réponse valable.</strong> Toutes les méthodes
 * rendent une liste vide plutôt que de lever : parcelle sans coordonnées,
 * fournisseur injoignable, météo désactivée. C'est la règle appliquée au
 * microservice d'inférence — perdre un diagnostic parce qu'un service tiers ne
 * répond pas serait absurde, et la même absurdité vaut ici.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    /**
     * Intervalle minimal entre deux purges globales.
     *
     * <p>Une heure : la fuite qu'elle corrige se compte en lignes par jour, pas
     * par minute. Purger à chaque rafraîchissement ferait payer un balayage de
     * table à des opérations qui n'y gagnent rien.
     */
    private static final Duration GLOBAL_PURGE_INTERVAL = Duration.ofHours(1);

    /**
     * Dernière purge globale.
     *
     * <p>En mémoire, et volontairement : ce n'est pas un état métier. Deux
     * instances purgeront chacune de leur côté, et un redémarrage remettra le
     * compteur à zéro — dans les deux cas la conséquence est une purge de plus,
     * ce qui est inoffensif. Persister cette date en base pour l'éviter coûterait
     * une table et une migration pour supprimer une opération idempotente.
     */
    private final java.util.concurrent.atomic.AtomicReference<Instant> lastGlobalPurge =
            new java.util.concurrent.atomic.AtomicReference<>(Instant.EPOCH);

    private final WeatherForecastRepository forecastRepository;
    private final WeatherClient weatherClient;
    private final BilangaProperties.Weather config;

    /**
     * Prévisions couvrant l'horizon configuré, du plus proche au plus lointain.
     *
     * @return liste vide si la météo est indisponible pour cette parcelle, pour
     *         quelque raison que ce soit. L'appelant n'a pas à distinguer les
     *         causes : dans tous les cas, il doit savoir se passer de la météo.
     */
    @Transactional
    public List<WeatherForecast> forecastFor(Plot plot) {
        if (!config.isEnabled() || plot == null
                || plot.getLatitude() == null || plot.getLongitude() == null) {
            return List.of();
        }

        refreshIfStale(plot);

        Instant now = Instant.now();
        return forecastRepository.findByPlot_IdAndForecastAtBetweenOrderByForecastAtAsc(
                plot.getId(), now, now.plus(Duration.ofHours(config.getHorizonHours())));
    }

    /**
     * Redemande au fournisseur si la dernière obtention date de trop.
     *
     * <p>La fraîcheur se juge sur {@code fetchedAt}, jamais sur la simple
     * présence de lignes : une table pleine de prévisions périmées passerait
     * sinon pour un cache valide, et le système raisonnerait indéfiniment sur la
     * météo de la semaine dernière.
     *
     * <p>Transaction séparée : l'échec du rafraîchissement ne doit pas remonter
     * jusqu'à la transaction du diagnostic, qui a toutes les raisons d'aboutir
     * sans météo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshIfStale(Plot plot) {
        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(config.getCacheTtlMinutes()));

        boolean fresh = forecastRepository.findLastFetchAt(plot.getId())
                .map(fetchedAt -> fetchedAt.isAfter(staleBefore))
                .orElse(false);

        if (fresh) {
            return;
        }

        try {
            List<HourlyForecast> hourly =
                    weatherClient.forecast(plot.getLatitude(), plot.getLongitude());

            // Les échéances dépassées n'ont plus d'usage, et sans purge la table
            // croît indéfiniment : ce cache ne porte pas d'historique météo.
            forecastRepository.deleteExpired(plot.getId(), Instant.now());

            purgeAbandonedPlotsOccasionally();

            Instant fetchedAt = Instant.now();
            for (HourlyForecast entry : hourly) {
                store(plot, entry, fetchedAt);
            }

        } catch (Exception e) {
            // Le cache existant, même périmé, vaut mieux que rien : une
            // prévision de pluie d'il y a trois heures reste plus informative
            // que l'absence totale de prévision.
            log.warn("Prévisions météo non rafraîchies pour la parcelle {} : {}",
                    plot.getId(), e.getMessage());
        }
    }

    /**
     * Purge les prévisions périmées de <strong>toutes</strong> les parcelles, au
     * plus une fois par heure (A14).
     *
     * <p><strong>La fuite corrigée.</strong> {@code deleteExpired} ne nettoie que
     * la parcelle en cours de rafraîchissement. Une parcelle qui cesse d'être
     * interrogée — culture terminée, boîtier retiré, exploitation archivée —
     * conservait donc indéfiniment ses prévisions : plus personne ne déclenchait le
     * nettoyage qui la concernait, <em>précisément parce que</em> plus personne ne
     * s'y intéressait. La table grossissait d'autant plus que les parcelles étaient
     * abandonnées, et rien dans le fonctionnement normal ne le signalait.
     *
     * <p><strong>Pourquoi ici et non dans un ordonnanceur.</strong> Le projet n'a
     * ni {@code @EnableScheduling} ni worker, et en introduire un pour cela seul
     * serait disproportionné — c'est exactement le scaffolding qu'on vient de
     * retirer du {@code pom.xml}. Accrocher la purge à une opération qui a lieu de
     * toute façon, en la bornant dans le temps, obtient le même résultat sans
     * nouvelle infrastructure.
     *
     * <p><strong>Pourquoi elle ne peut rien casser.</strong> Elle s'exécute
     * <em>après</em> le stockage des nouvelles prévisions, ne supprime que des
     * échéances déjà passées, et tout échec est avalé : une purge manquée n'est
     * qu'une purge reportée d'une heure, là où un rafraîchissement manqué priverait
     * le diagnostic de sa météo.
     */
    private void purgeAbandonedPlotsOccasionally() {
        Instant now = Instant.now();
        Instant last = lastGlobalPurge.get();

        if (last.plus(GLOBAL_PURGE_INTERVAL).isAfter(now)) {
            return;
        }
        // compareAndSet : deux ingestions simultanées ne déclenchent qu'une purge.
        if (!lastGlobalPurge.compareAndSet(last, now)) {
            return;
        }

        try {
            int removed = forecastRepository.purgeExpired(now);
            if (removed > 0) {
                log.debug("Purge météo globale : {} échéance(s) périmée(s) supprimée(s), "
                        + "dont celles de parcelles qui ne sont plus interrogées.", removed);
            }
        } catch (Exception e) {
            log.warn("Purge météo globale ignorée : {}", e.getMessage());
        }
    }

    /**
     * Écrit ou met à jour l'échéance.
     *
     * L'unicité {@code (plot_id, forecast_at)} interdit d'empiler : une
     * prévision réactualisée <em>remplace</em> la précédente, sinon la table
     * accumulerait autant de versions que d'appels et la lecture rendrait des
     * doublons contradictoires.
     */
    private void store(Plot plot, HourlyForecast entry, Instant fetchedAt) {
        WeatherForecast forecast = forecastRepository
                .findByPlot_IdAndForecastAt(plot.getId(), entry.at())
                .orElseGet(() -> WeatherForecast.builder()
                        .plot(plot)
                        .forecastAt(entry.at())
                        .build());

        forecast.setTemperature(entry.temperature());
        forecast.setHumidite(entry.humidite());
        forecast.setPrecipitationMm(entry.precipitationMm());
        forecast.setWindSpeed(entry.windSpeed());
        forecast.setCloudCover(entry.cloudCover());
        forecast.setProvider(weatherClient.provider());
        forecast.setFetchedAt(fetchedAt);

        forecastRepository.save(forecast);
    }
}
