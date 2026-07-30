package com.sni.bilanga.diagnosis.client.support;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.exception.customs.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Transport partagé par les clients du microservice d'inférence.
 *
 * Les deux clients répétaient le même échange HTTP avec les mêmes défauts :
 * aucun délai d'attente — une inférence lente immobilisait le fil d'ingestion
 * jusqu'au délai du système — et aucune reprise sur incident réseau passager.
 *
 * La reprise ne vaut que pour les pannes de transport. Un statut HTTP non-200
 * est une réponse : le service a compris la demande et l'a refusée ; la répéter
 * ne ferait que doubler la charge sur un service qui va déjà mal.
 */
@Slf4j
@Component
public class MlHttpExchange {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final int maxAttempts;
    private final Duration retryBackoff;

    public MlHttpExchange(BilangaProperties.Ml ml) {
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(ml.getConnectTimeoutSeconds()))
                .build();
        this.maxAttempts = ml.getMaxAttempts();
        this.retryBackoff = Duration.ofMillis(ml.getRetryBackoffMillis());
    }

    /**
     * Poste {@code payload} en JSON et désérialise la réponse.
     *
     * @param label mention lisible du modèle appelé, reprise dans le message d'erreur
     * @throws ServiceUnavailableException dès que le résultat n'est pas exploitable ;
     *         l'appelant décide seul s'il peut se passer de l'inférence
     */
    public <T> T post(String url, Object payload, Class<T> responseType,
                      Duration timeout, String label) {

        String json = serialize(payload, label);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        IOException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw ServiceUnavailableException.machineLearning(
                            String.format("Modèle %s : le service a répondu %d.", label, response.statusCode()),
                            null);
                }
                return deserialize(response.body(), responseType, label);

            } catch (IOException e) {
                lastFailure = e;
                log.warn("Appel au modèle {} en échec (tentative {}/{}) : {}",
                        label, attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    pause();
                }

            } catch (InterruptedException e) {
                // Le fil est en cours d'arrêt : restaurer l'indicateur et abandonner.
                Thread.currentThread().interrupt();
                throw ServiceUnavailableException.machineLearning(
                        "Appel au modèle " + label + " interrompu.", e);
            }
        }

        throw ServiceUnavailableException.machineLearning(
                String.format("Modèle %s injoignable après %d tentative%s.",
                        label, maxAttempts, maxAttempts > 1 ? "s" : ""),
                lastFailure);
    }

    private String serialize(Object payload, String label) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (RuntimeException e) {
            // Défaut de construction de la requête, pas une panne du service.
            throw new IllegalStateException("Sérialisation de la requête " + label + " impossible", e);
        }
    }

    private <T> T deserialize(String body, Class<T> responseType, String label) {
        try {
            return mapper.readValue(body, responseType);
        } catch (RuntimeException e) {
            throw ServiceUnavailableException.machineLearning(
                    "Réponse du modèle " + label + " illisible.", e);
        }
    }

    private void pause() {
        if (retryBackoff.isZero()) {
            return;
        }
        try {
            Thread.sleep(retryBackoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
