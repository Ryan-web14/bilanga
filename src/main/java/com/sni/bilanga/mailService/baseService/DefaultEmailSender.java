package com.sni.bilanga.mailService.baseService;

import com.sni.bilanga.config.properties.BilangaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Envoi de courrier par <strong>Microsoft Graph</strong>.
 *
 * <h2>Pourquoi Graph, et pas SMTP</h2>
 *
 * <p>Le canal courriel des notifications parle SMTP avec {@code AUTH LOGIN} — identifiant
 * et mot de passe en base64. <strong>Microsoft 365 a désactivé l'authentification basique
 * SMTP sur la plupart des locataires</strong> : sur un locataire moderne, ce transport
 * échoue à l'authentification quelles que soient les valeurs fournies, et aucune
 * configuration ne le rattrape. Graph est la voie que Microsoft maintient.
 *
 * <h2>Pourquoi sans le SDK Microsoft</h2>
 *
 * <p>La version d'origine employait {@code azure-identity} et {@code microsoft-graph}. Deux
 * raisons de ne pas les reprendre :
 *
 * <ul>
 *   <li><strong>Le poids.</strong> Les deux SDK et leurs transitives pèsent plusieurs
 *       dizaines de mégaoctets, sur une plateforme dont le paquet déployé est plafonné à
 *       500 Mo.</li>
 *   <li><strong>La cohérence.</strong> Le projet parle déjà à trois services tiers — le
 *       microservice d'inférence, Open-Meteo, la passerelle SMS — et les trois emploient le
 *       {@code HttpClient} du JDK. Le besoin est ici le même : deux appels HTTP, un jeton,
 *       un corps JSON.</li>
 * </ul>
 *
 * <h2>Le flux</h2>
 *
 * <pre>
 * 1. POST login.microsoftonline.com/{tenant}/oauth2/v2.0/token   → jeton (≈ 1 h)
 * 2. POST graph.microsoft.com/v1.0/users/{sender}/sendMail       → 202 Accepted
 * </pre>
 *
 * <p><strong>Le jeton est mis en cache</strong> et renouvelé deux minutes avant son terme.
 * Sans cela, chaque courriel paierait un aller-retour d'authentification — et le
 * fournisseur limite ces appels bien plus sévèrement que les envois.
 *
 * <h2>Ce qu'il faut côté Azure</h2>
 *
 * <p>La permission <strong>d'application</strong> {@code Mail.Send} — pas la permission
 * déléguée — avec <strong>consentement administrateur</strong>. Sans ce consentement, le
 * jeton est délivré normalement et l'envoi répond 403 : le symptôme désigne l'envoi alors
 * que la cause est dans le portail.
 */
@Slf4j
@Component
public class DefaultEmailSender {

    private static final String TOKEN_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String SEND_URL = "https://graph.microsoft.com/v1.0/users/%s/sendMail";
    private static final String SCOPE = "https://graph.microsoft.com/.default";

    /**
     * Marge de renouvellement. Un jeton qui expire pendant le vol d'une requête produit un
     * 401 qu'aucune reprise ne distingue d'un secret invalide.
     */
    private static final Duration RENEWAL_MARGIN = Duration.ofMinutes(2);

    private final BilangaProperties.Email.Graph config;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Jeton courant et son échéance. {@code volatile} : plusieurs fils peuvent envoyer. */
    private volatile String token;
    private volatile Instant expiresAt = Instant.EPOCH;

    private final com.sni.bilanga.mailService.service.EmailDeliveryTracker tracker;

    public DefaultEmailSender(BilangaProperties.Email email,
                              com.sni.bilanga.mailService.service.EmailDeliveryTracker tracker) {
        this.tracker = tracker;
        this.config = email.getGraph();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.config.getConnectTimeoutSeconds()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Vrai si les quatre valeurs nécessaires sont présentes.
     *
     * <p>Même contrat que le SMS et SMTP : <strong>non configuré ⇒ indisponible</strong>, et
     * rien ne lui est confié. Un transport qui se déclare prêt sans l'être accumulerait des
     * échecs pour rien.
     */
    public boolean isConfigured() {
        return notBlank(config.getClientId())
                && notBlank(config.getClientSecret())
                && notBlank(config.getTenantId())
                && notBlank(config.getSenderEmail());
    }

    /** Adresse expéditrice annoncée, pour les journaux et le suivi de remise. */
    public String senderEmail() {
        return config.getSenderEmail();
    }

    /**
     * Envoie un message. {@code html} décide du type de contenu annoncé.
     *
     * @throws IllegalStateException si le transport n'est pas configuré, ou si Microsoft
     *                               refuse — la reprise est la charge de l'appelant, jamais
     *                               celle du transport
     */
    public void send(String to, String subject, String body, boolean html) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Microsoft Graph n'est pas configuré : client-id, client-secret, "
                            + "tenant-id et sender-email sont tous requis.");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Aucune adresse de destinataire.");
        }

        String payload = mapper.writeValueAsString(Map.of(
                "message", Map.of(
                        "subject", subject == null ? "" : subject,
                        "body", Map.of(
                                "contentType", html ? "HTML" : "Text",
                                "content", body == null ? "" : body),
                        "toRecipients", List.of(
                                Map.of("emailAddress", Map.of("address", to)))),
                // false : la boîte d'envoi d'un compte de service n'a pas vocation à
                // conserver les notifications, et l'écriture coûte un appel de plus.
                "saveToSentItems", Boolean.FALSE));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(SEND_URL,
                        URLEncoder.encode(config.getSenderEmail(), StandardCharsets.UTF_8))))
                .timeout(Duration.ofSeconds(config.getReadTimeoutSeconds()))
                .header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = exchange(request, "envoi");

        // Graph répond 202 : le message est ACCEPTÉ, pas encore remis. Aucune API ne permet
        // de savoir depuis ici s'il a atteint la boîte du destinataire — c'est une limite
        // du transport, et le suivi de remise ne dit rien de plus.
        if (response.statusCode() != 202 && response.statusCode() != 200) {
            throw new IllegalStateException(describeFailure(response));
        }
        log.debug("Courriel remis à Graph pour {} (HTTP {})", to, response.statusCode());
    }

    // ============================================================
    // Service : envoi suivi, et reprise
    // ============================================================

    /**
     * Enfile, envoie, et consigne — <strong>de façon asynchrone</strong>.
     *
     * <p>L'appelant reçoit la main immédiatement. C'est nécessaire : la demande d'un code
     * de connexion ne doit pas attendre qu'un service tiers ait répondu. Un utilisateur qui
     * patiente trois secondes devant un formulaire recommence, et déclenche un second envoi.
     *
     * <p>La ligne de suivi est écrite <strong>avant</strong> l'appel, dans sa propre
     * transaction. Un échec d'envoi laisse donc une trace {@code FAILED} consultable et
     * rejouable, là où une exception seule ne laisserait qu'une ligne de journal.
     *
     * <p>{@code priority} n'influence pas le transport — Graph ne l'expose pas. Elle est
     * consignée pour que l'exploitation sache ce qui méritait de passer devant.
     */
    @org.springframework.scheduling.annotation.Async
    public java.util.concurrent.CompletableFuture<Boolean> sendHtmlEmail(
            String to, String subject, String html,
            com.sni.bilanga.mailService.enums.EmailPriority priority) {

        return java.util.concurrent.CompletableFuture.completedFuture(
                deliver(to, subject, html, priority == null ? null : priority.name()));
    }

    /**
     * Rejoue un envoi consigné en échec.
     *
     * <p>Le contenu est relu depuis la ligne de suivi, jamais reconstruit : reconstruire
     * demanderait de rejouer le gabarit avec des variables qu'on n'a plus, et produirait un
     * message différent de celui qu'on croit renvoyer.
     */
    public com.sni.bilanga.mailService.dto.response.EmailDeliveryResponse retry(String emailNumber) {
        var log = tracker.getEntity(emailNumber);
        tracker.resetForRetry(emailNumber);

        boolean sent = deliverTracked(emailNumber, log.getRecipientEmail(), log.getSubject(),
                log.getBodyContent(), "HTML".equalsIgnoreCase(log.getBodyType()));

        return tracker.get(emailNumber);
    }

    /** Enfile puis remet, en consignant chaque étape. */
    private boolean deliver(String to, String subject, String body, String priority) {
        var queued = tracker.queue("MICROSOFT_GRAPH", senderEmail(), to, subject,
                "HTML", body, "NOTIFICATION", priority);

        return deliverTracked(queued.emailNumber(), to, subject, body, true);
    }

    private boolean deliverTracked(String emailNumber, String to, String subject,
                                   String body, boolean html) {
        try {
            tracker.markSending(emailNumber);
            send(to, subject, body, html);
            tracker.markSent(emailNumber);
            return true;

        } catch (RuntimeException failure) {
            // L'échec est consigné et non propagé : l'appelant est un flux asynchrone qui
            // n'a personne à qui remonter l'exception. La ligne FAILED est ce qui rend
            // l'incident visible et rejouable.
            log.error("Échec d'envoi {} vers {} : {}", emailNumber, to, failure.getMessage());
            tracker.markFailed(emailNumber, failure.getMessage());
            return false;
        }
    }

    // ============================================================
    // Jeton
    // ============================================================

    /**
     * Jeton courant, renouvelé s'il approche de son terme.
     *
     * <p>{@code synchronized} sur le seul renouvellement : deux envois simultanés au moment
     * de l'expiration demanderaient sinon deux jetons, et le fournisseur limite ces appels
     * bien plus sévèrement que les envois eux-mêmes.
     */
    private String accessToken() {
        if (isTokenFresh()) {
            return token;
        }
        synchronized (this) {
            return isTokenFresh() ? token : renewToken();
        }
    }

    private boolean isTokenFresh() {
        return token != null && Instant.now().isBefore(expiresAt.minus(RENEWAL_MARGIN));
    }

    private String renewToken() {
        String form = "client_id=" + encode(config.getClientId())
                + "&client_secret=" + encode(config.getClientSecret())
                + "&scope=" + encode(SCOPE)
                + "&grant_type=client_credentials";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(TOKEN_URL, config.getTenantId())))
                .timeout(Duration.ofSeconds(config.getReadTimeoutSeconds()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = exchange(request, "authentification");

        if (response.statusCode() != 200) {
            // Le corps porte error et error_description, qui nomment la cause réelle :
            // secret expiré, locataire inconnu, consentement manquant. Le taire
            // obligerait à deviner.
            throw new IllegalStateException(
                    "Authentification Microsoft refusée : " + describeFailure(response));
        }

        Map<?, ?> body = mapper.readValue(response.body(), Map.class);
        Object accessToken = body.get("access_token");
        Object expiresIn = body.get("expires_in");

        if (accessToken == null) {
            throw new IllegalStateException(
                    "Réponse d'authentification sans access_token : " + describeFailure(response));
        }

        long seconds = expiresIn instanceof Number number ? number.longValue() : 3600L;
        this.token = String.valueOf(accessToken);
        this.expiresAt = Instant.now().plusSeconds(seconds);

        log.info("Jeton Microsoft Graph obtenu, valable {} minutes.", seconds / 60);
        return this.token;
    }

    // ============================================================
    // Interne
    // ============================================================

    private HttpResponse<String> exchange(HttpRequest request, String label) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envoi interrompu.", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Microsoft Graph injoignable (" + label + ") : " + failure.getMessage(),
                    failure);
        }
    }

    /**
     * Message d'échec, <strong>tronqué</strong>.
     *
     * <p>Les réponses d'erreur portent des identifiants de requête et parfois des fragments
     * de configuration ; les journaliser en entier revient à recopier de la configuration
     * dans des journaux qu'on partage plus volontiers qu'elle.
     */
    private String describeFailure(HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body();
        if (body.length() > 400) {
            body = body.substring(0, 400) + "…";
        }
        return "HTTP " + response.statusCode() + " · " + body;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
