package com.sni.bilanga.weather.client.implementation;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.exception.customs.ServiceUnavailableException;
import com.sni.bilanga.weather.client.dto.response.HourlyForecast;
import com.sni.bilanga.weather.client.interfaces.WeatherClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client Open-Meteo.
 *
 * <p><strong>Pourquoi ce fournisseur.</strong> Il ne demande aucune clé d'API ni
 * inscription. Le système est donc démontrable sans compte à gérer, et sans
 * abonnement susceptible d'expirer entre l'écriture du mémoire et la
 * soutenance — un risque qui n'a rien d'hypothétique.
 *
 * <p><strong>Le patron est celui de {@code MlHttpExchange}</strong> : JDK
 * {@code HttpClient}, délais d'attente courts, reprise bornée aux seules pannes
 * de transport, {@code ServiceUnavailableException} dès que le résultat n'est
 * pas exploitable. Une réponse non-2xx n'est pas réessayée : le service a
 * compris la demande et l'a refusée ; la répéter ne ferait que doubler la charge
 * sur un service qui va déjà mal.
 */
@Slf4j
@Service
public class OpenMeteoClient implements WeatherClient {

    public static final String PROVIDER = "OPEN_METEO";

    /**
     * Les échéances sont demandées en UTC.
     *
     * Sans ce paramètre, Open-Meteo renvoie des heures locales sans décalage :
     * impossibles à convertir sans deviner le fuseau, et sources d'un décalage
     * silencieux d'une heure ou plus dans toutes les projections.
     */
    private static final String HOURLY_FIELDS =
            "temperature_2m,relative_humidity_2m,precipitation,cloud_cover,wind_speed_10m";

    private final ObjectMapper mapper = new ObjectMapper();
    private final BilangaProperties.Weather config;
    private final HttpClient http;

    public OpenMeteoClient(BilangaProperties.Weather config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(config.getConnectTimeoutSeconds()))
                .build();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public List<HourlyForecast> forecast(double latitude, double longitude) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildUrl(latitude, longitude)))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(config.getReadTimeoutSeconds()))
                .GET()
                .build();

        IOException lastFailure = null;

        for (int attempt = 1; attempt <= config.getMaxAttempts(); attempt++) {
            try {
                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new ServiceUnavailableException(
                            "Le service météo a répondu " + response.statusCode() + ".",
                            com.sni.bilanga.utils.error.ErrorCode.SERVICE_UNAVAILABLE);
                }
                return parse(response.body());

            } catch (IOException e) {
                lastFailure = e;
                log.warn("Appel au service météo en échec (tentative {}/{}) : {}",
                        attempt, config.getMaxAttempts(), e.getMessage());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceUnavailableException("Appel au service météo interrompu.");
            }
        }

        throw new ServiceUnavailableException(
                "Service météo injoignable après " + config.getMaxAttempts() + " tentatives.",
                com.sni.bilanga.utils.error.ErrorCode.SERVICE_UNAVAILABLE, lastFailure);
    }

    private String buildUrl(double latitude, double longitude) {
        // Locale.ROOT : sous une locale française, %f produirait une virgule
        // décimale, que l'API rejette. Le défaut de la machine n'a pas à
        // décider du format d'une URL.
        return String.format(Locale.ROOT,
                "%s/v1/forecast?latitude=%.6f&longitude=%.6f&hourly=%s&timezone=UTC&forecast_days=%d",
                config.getBaseUrl().replaceAll("/+$", ""),
                latitude, longitude, HOURLY_FIELDS,
                // Arrondi supérieur : demander deux jours pour un horizon de 30 h
                // coûte le même appel et évite de manquer la fin de la fenêtre.
                Math.max(1, (int) Math.ceil(config.getHorizonHours() / 24d)));
    }

    /**
     * Transpose la réponse en tableaux parallèles vers une liste d'échéances.
     *
     * <p>Open-Meteo rend des colonnes ({@code time[]}, {@code temperature_2m[]},
     * …) et non des lignes. Une valeur manquante est représentée par
     * {@code null} dans le tableau : elle reste {@code null} ici plutôt que de
     * devenir zéro — « pas de donnée » et « zéro millimètre de pluie » ne se
     * confondent pas, et le moteur en tire des conclusions opposées.
     */
    private List<HourlyForecast> parse(String body) {
        try {
            JsonNode hourly = mapper.readTree(body).path("hourly");
            JsonNode times = hourly.path("time");

            if (!times.isArray() || times.isEmpty()) {
                throw new ServiceUnavailableException(
                        "Réponse du service météo sans échéance exploitable.");
            }

            JsonNode temperature = hourly.path("temperature_2m");
            JsonNode humidity = hourly.path("relative_humidity_2m");
            JsonNode precipitation = hourly.path("precipitation");
            JsonNode cloud = hourly.path("cloud_cover");
            JsonNode wind = hourly.path("wind_speed_10m");

            List<HourlyForecast> forecasts = new ArrayList<>(times.size());
            for (int i = 0; i < times.size(); i++) {
                forecasts.add(new HourlyForecast(
                        LocalDateTime.parse(times.get(i).asString()).toInstant(ZoneOffset.UTC),
                        doubleAt(temperature, i),
                        doubleAt(humidity, i),
                        doubleAt(precipitation, i),
                        doubleAt(wind, i),
                        doubleAt(cloud, i)));
            }
            return forecasts;

        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException(
                    "Réponse du service météo illisible.",
                    com.sni.bilanga.utils.error.ErrorCode.SERVICE_UNAVAILABLE, e);
        }
    }

    private Double doubleAt(JsonNode array, int index) {
        if (!array.isArray() || index >= array.size()) {
            return null;
        }
        JsonNode value = array.get(index);
        return value == null || value.isNull() ? null : value.asDouble();
    }
}
