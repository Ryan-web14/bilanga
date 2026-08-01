package com.sni.bilanga.diagnosis.client.support;

import com.sni.bilanga.config.properties.BilangaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Tient le microservice d'inférence éveillé tant que le backend l'est.
 *
 * <h2>Le problème, précisément</h2>
 *
 * <p>Un dyno hébergé s'endort après une période d'inactivité. Le premier appel qui suit
 * paie le démarrage complet — runtime, poids des modèles — soit vingt à trente secondes.
 * Or {@code MlHttpExchange} coupe à trente secondes sur la vision, et la plateforme coupe
 * elle-même à trente secondes sans que ce soit configurable.
 *
 * <p><strong>Le premier diagnostic après une mise en veille échoue donc presque à coup
 * sûr</strong> — et il échoue en {@code ML_INDISPONIBLE}, c'est-à-dire de la même façon
 * qu'une panne réelle. Rien ne distingue « le service dormait » de « le service est
 * cassé », et c'est toujours le premier appel d'une démonstration qui en fait les frais.
 *
 * <h2>Ce que cette tâche fait, et surtout ce qu'elle ne fait pas</h2>
 *
 * <p>Elle <strong>n'empêche pas</strong> le service de s'endormir. Elle le réveille tant
 * que le backend tourne — ce qui est exactement la garantie utile : quelqu'un qui
 * interroge le backend interrogera l'inférence dans la minute.
 *
 * <p>Le tenir éveillé la nuit consommerait des heures de dyno sans que personne n'en
 * profite. Et le backend s'endort lui aussi : quand les deux dorment, plus rien n'appelle
 * plus rien — c'est le comportement voulu, pas une lacune.
 *
 * <h2>Elle ne peut rien casser</h2>
 *
 * <p>Aucune exception ne remonte : un réveil manqué n'est pas un incident, c'est un
 * confort en moins. Le diagnostic continuera de se dégrader proprement en
 * {@code ML_INDISPONIBLE}, comme il le faisait avant l'existence de cette classe.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "bilanga.ml.warmup", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public class MlWarmupTask {

    private final BilangaProperties.Ml ml;
    private final HttpClient http;

    /** Pour ne journaliser un changement d'état qu'une fois, et non à chaque tour. */
    private volatile Boolean lastReachable;

    public MlWarmupTask(BilangaProperties.Ml ml) {
        this.ml = ml;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ml.getWarmup().getTimeoutSeconds()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Un tour de réveil.
     *
     * <p>Volontairement <strong>sans</strong> {@code @Scheduled} : la planification est
     * enregistrée par {@code SchedulingConfig}, qui calcule l'intervalle en Java depuis
     * les propriétés. Une expression {@code fixedDelayString} ne se serait vérifiée qu'au
     * démarrage, et une erreur d'expression y empêche l'application de démarrer — pour
     * une tâche de confort, c'est un risque sans contrepartie.
     */
    public void keepAwake() {
        String url = ml.getBaseUrl() + ml.getWarmup().getPath();
        Instant start = Instant.now();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(ml.getWarmup().getTimeoutSeconds()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<Void> response =
                    http.send(request, HttpResponse.BodyHandlers.discarding());

            long millis = Duration.between(start, Instant.now()).toMillis();
            boolean reachable = response.statusCode() == 200;

            // Un réveil qui a pris plus de cinq secondes est un réveil réel — le service
            // dormait. Le dire une fois vaut mieux que de le taire : c'est l'information
            // qui explique pourquoi le diagnostic suivant aurait échoué sans cette tâche.
            if (reachable && millis > 5_000) {
                log.info("Microservice d'inférence RÉVEILLÉ en {} ms ({}). "
                        + "Le premier diagnostic aurait probablement expiré sans cela.",
                        millis, url);
            } else {
                log.debug("Microservice d'inférence joignable en {} ms ({})", millis, url);
            }

            announceChange(reachable, url, null);

        } catch (Exception unreachable) {
            // Interruption : on restaure le drapeau et on sort sans bruit — l'application
            // s'arrête, ce n'est pas une panne du service distant.
            if (unreachable instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
            announceChange(false, url, unreachable.getMessage());
        }
    }

    /**
     * Ne journalise que les <strong>changements</strong> d'état.
     *
     * <p>Un service injoignable pendant une nuit produirait autrement une ligne toutes les
     * vingt minutes, et le jour où l'incident compte vraiment, personne ne le verrait dans
     * le bruit.
     */
    private void announceChange(boolean reachable, String url, String cause) {
        if (Boolean.valueOf(reachable).equals(lastReachable)) {
            return;
        }
        lastReachable = reachable;

        if (reachable) {
            log.info("Microservice d'inférence de nouveau joignable ({}).", url);
        } else {
            log.warn("Microservice d'inférence INJOIGNABLE ({}) : {}. Les diagnostics se "
                    + "dégraderont en ML_INDISPONIBLE : les relevés restent enregistrés.",
                    url, cause == null ? "réponse inattendue" : cause);
        }
    }
}
