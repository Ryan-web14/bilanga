package com.sni.bilanga.notification.channel;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.notification.model.NotificationOutbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Envoi de SMS par une passerelle HTTP décrite en configuration.
 *
 * <p><strong>Le canal qui compte réellement.</strong> Le SMS fonctionne sur
 * téléphone simple, sans forfait données, avec une couverture bien supérieure à
 * celle de l'internet mobile. C'est lui qui atteint l'exploitant au champ, là où
 * une notification applicative ne sera lue que le soir, si elle l'est.
 *
 * <p><strong>Pourquoi aucun SDK d'opérateur.</strong> Africa's Talking, Twilio
 * et les passerelles locales exposent toutes la même chose : une URL, un corps
 * portant un numéro et un texte, un en-tête d'autorisation. Un client par
 * opérateur reviendrait à réécrire trois fois le même appel HTTP — et à devoir
 * livrer du code pour changer de fournisseur, au moment précis où l'ancien ne
 * marche plus. Ici, changer d'opérateur est une modification de fichier de
 * configuration.
 *
 * <p><strong>Inerte tant qu'il n'est pas configuré.</strong>
 * {@link #isAvailable()} répond faux si l'URL est vide, et
 * {@code NotificationService} ne lui enfile alors rien : aucune ligne en attente,
 * aucun échec compté, rien à nettoyer le jour où l'on branche une vraie
 * passerelle. Le système est donc démontrable sans compte opérateur.
 *
 * <p>Conformément au contrat de {@link NotificationChannel}, un échec lève
 * simplement une exception : la reprise appartient à l'expéditeur, pas au canal.
 */
@Slf4j
@Component
public class HttpSmsChannel implements NotificationChannel {

    public static final String NAME = "SMS";

    private static final String PLACEHOLDER_TO = "{{to}}";
    private static final String PLACEHOLDER_BODY = "{{body}}";
    private static final String PLACEHOLDER_FROM = "{{from}}";

    private final BilangaProperties.Sms config;
    private final HttpClient http;

    public HttpSmsChannel(BilangaProperties.Sms config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(config.getConnectTimeoutSeconds()))
                .build();
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Une URL vide suffit à désactiver le canal : c'est le seul interrupteur qui
     * compte, et il évite d'avoir à tenir deux réglages cohérents entre eux.
     */
    @Override
    public boolean isAvailable() {
        return config.isEnabled() && config.getUrl() != null && !config.getUrl().isBlank();
    }

    @Override
    public void send(NotificationOutbox notification) {
        String recipient = notification.getRecipient();
        if (recipient == null || recipient.isBlank()) {
            // Ce n'est pas une panne de la passerelle : réessayer n'y changerait
            // rien. L'exception fait basculer la ligne en échec, et le motif dit
            // où corriger — sur la fiche de l'utilisateur, pas sur le serveur.
            throw new IllegalStateException(
                    "Aucun numéro de téléphone pour cette notification. "
                    + "Renseignez le téléphone du propriétaire de la parcelle.");
        }

        HttpRequest request = buildRequest(normalize(recipient), compose(notification));

        IOException lastFailure = null;
        for (int attempt = 1; attempt <= config.getMaxAttempts(); attempt++) {
            try {
                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    // Une réponse non-2xx est une décision de la passerelle, pas
                    // un incident de transport : la répéter ne ferait que
                    // consommer du crédit et de la patience.
                    throw new IllegalStateException(String.format(
                            "La passerelle SMS a répondu %d : %s",
                            response.statusCode(), truncate(response.body())));
                }
                return;

            } catch (IOException e) {
                lastFailure = e;
                log.warn("Envoi SMS en échec (tentative {}/{}) : {}",
                        attempt, config.getMaxAttempts(), e.getMessage());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Envoi SMS interrompu.", e);
            }
        }

        throw new IllegalStateException(
                "Passerelle SMS injoignable après " + config.getMaxAttempts() + " tentatives.",
                lastFailure);
    }

    // ============================================================
    // Construction de la requête
    // ============================================================
    private HttpRequest buildRequest(String recipient, String message) {
        String body = config.getBodyTemplate()
                .replace(PLACEHOLDER_TO, jsonEscape(recipient))
                .replace(PLACEHOLDER_BODY, jsonEscape(message))
                .replace(PLACEHOLDER_FROM, jsonEscape(config.getSender()));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getUrl()))
                .timeout(Duration.ofSeconds(config.getReadTimeoutSeconds()))
                .header("Content-Type", config.getContentType())
                .method(config.getMethod().toUpperCase(java.util.Locale.ROOT),
                        HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        for (Map.Entry<String, String> header : config.getHeaders().entrySet()) {
            if (header.getValue() != null && !header.getValue().isBlank()) {
                builder.header(header.getKey(), header.getValue());
            }
        }
        return builder.build();
    }

    /**
     * Compose le texte du SMS.
     *
     * <p>Sujet et corps sont réunis en un seul message : un SMS n'a pas d'objet.
     * Le tout est tronqué, car un message long est facturé plusieurs fois et
     * arrive parfois découpé dans le désordre — mieux vaut un texte écourté et
     * lisible qu'un texte complet reçu en trois morceaux mélangés.
     */
    private String compose(NotificationOutbox notification) {
        String subject = notification.getSubject() == null ? "" : notification.getSubject();
        String body = notification.getBody() == null ? "" : notification.getBody();

        String message = subject.isBlank() ? body : subject + " — " + body;
        if (message.length() <= config.getMaxLength()) {
            return message;
        }
        return message.substring(0, config.getMaxLength() - 3) + "...";
    }

    /**
     * Met le numéro en forme internationale.
     *
     * <p>Les numéros sont saisis comme on les dicte — {@code 06 123 45 67} — et
     * aucune passerelle n'accepte cette forme. La conversion est faite ici
     * plutôt qu'à la saisie : imposer le format international au moment de créer
     * un compte ferait échouer des saisies parfaitement valides du point de vue
     * de qui les tape.
     */
    private String normalize(String raw) {
        String digits = raw.replaceAll("[^0-9+]", "");

        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.startsWith("00")) {
            return "+" + digits.substring(2);
        }
        // Forme locale : le zéro initial est un préfixe national, remplacé par
        // l'indicatif du pays.
        String national = digits.startsWith("0") ? digits.substring(1) : digits;
        return config.getDefaultCountryCode() + national;
    }

    /**
     * Échappe le texte pour insertion dans le gabarit JSON.
     *
     * Un message d'alerte porte des guillemets, des accents et des retours à la
     * ligne ; insérés tels quels, ils produiraient un corps JSON invalide que la
     * passerelle rejetterait avec un message peu explicite.
     */
    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 197) + "...";
    }
}
