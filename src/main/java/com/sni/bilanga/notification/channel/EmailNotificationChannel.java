package com.sni.bilanga.notification.channel;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.notification.model.NotificationOutbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

/**
 * Envoi de courriels par SMTP.
 *
 * <p><strong>Pourquoi ce canal existe, et pourquoi après le SMS.</strong> Le
 * courriel est le canal évident pour un développeur, et le moins utile au champ :
 * il suppose un forfait données, un client de messagerie configuré et l'habitude
 * de le consulter. Un exploitant lit un SMS dans la minute et un courriel le soir,
 * s'il le lit. Le SMS a donc été livré au rang 3, celui-ci vient après.
 *
 * <p><strong>Ce que le courriel apporte : la place.</strong> Un SMS est tronqué à
 * 320 caractères, et le constat agronomique complet — mesures, seuils, écarts —
 * y entre rarement. C'est le canal du <em>conseiller</em> et du technicien plus
 * que celui de l'exploitant. Il est particulièrement adapté aux alertes
 * {@code TECHNIQUE}, dont le motif rédigé par {@code SensorHealthAnalyzer} dit
 * quelle sonde changer et pourquoi.
 *
 * <p><strong>Il remplace aussi un envoi commenté (A15).</strong> Les envois de
 * courriel étaient commentés dans le code d'authentification, si bien que les
 * codes de connexion à usage unique et de réinitialisation revenaient
 * <em>dans la réponse de l'API</em> — un contournement de développement qui, laissé
 * en place, rendrait tout compte accessible à quiconque sait appeler la route.
 * Ce canal fournit le transport qui manquait ; le rebranchement côté
 * authentification reste à faire et est signalé dans les documents de suivi.
 *
 * <h2>Pourquoi du SMTP écrit à la main</h2>
 *
 * <p>{@code spring-boot-starter-mail} serait le choix habituel. Il n'est pas
 * retenu ici pour la même raison que les dix-neuf artefacts retirés du
 * {@code pom.xml} au lot 5 : ce projet vient de se débarrasser de dépendances
 * qu'aucun code n'utilisait, et en ajouter une pour un unique envoi de texte brut
 * irait à l'encontre du nettoyage. Le protocole employé ici — {@code EHLO},
 * {@code STARTTLS}, {@code AUTH LOGIN}, {@code DATA} — est celui que tout
 * fournisseur accepte, et il tient en une classe.
 *
 * <p><strong>La contrepartie est assumée</strong> : pas de pièce jointe, pas de
 * corps HTML, pas de multipart. Le jour où l'un devient nécessaire — un rapport
 * PDF en annexe, par exemple — le starter reprendra ses droits, et
 * {@link NotificationChannel} l'accueillera sans que rien d'autre ne change.
 *
 * <p><strong>Inerte tant qu'il n'est pas configuré.</strong> {@link #isAvailable()}
 * répond faux sur un hôte vide, et {@code NotificationService} ne lui enfile alors
 * rien : aucune ligne en attente, aucun échec compté. C'est l'invariant du projet,
 * partagé avec le SMS et la météo — une capacité indisponible retire une
 * capacité, elle ne casse rien.
 */
@Slf4j
@Component
public class EmailNotificationChannel implements NotificationChannel {

    public static final String NAME = "EMAIL";

    private static final String CRLF = "\r\n";

    /**
     * Format de date exigé par la RFC 5322.
     *
     * <p>Un en-tête {@code Date} absent ou mal formé fait classer le message en
     * indésirable par la plupart des filtres — ce qui produirait le pire des
     * résultats : un canal qui se déclare disponible, n'échoue jamais, et dont
     * aucun message n'arrive.
     */
    private static final DateTimeFormatter RFC_5322 =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
                    .withZone(java.time.ZoneOffset.UTC);

    private final BilangaProperties.Email config;

    /**
     * Transport Microsoft Graph, employé de préférence à SMTP quand il est configuré.
     * Le même que celui des codes de connexion : un seul chemin d'envoi à surveiller.
     */
    private final com.sni.bilanga.mailService.baseService.DefaultEmailSender graph;

    public EmailNotificationChannel(BilangaProperties.Email config,
                                    com.sni.bilanga.mailService.baseService.DefaultEmailSender graph) {
        this.config = config;
        this.graph = graph;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * L'hôte, et non {@code enabled}, décide en dernier ressort.
     *
     * <p>Un canal « activé » sans serveur accumulerait des échecs pour rien, et
     * {@code availableChannels} annoncerait au frontend une capacité qui n'existe
     * pas — le client proposerait alors de cocher « courriel » sans qu'aucun
     * message ne puisse partir.
     */
    @Override
    public boolean isAvailable() {
        if (!config.isEnabled()) {
            return false;
        }
        // Deux transports possibles, un seul suffit. Graph d'abord : c'est le seul qui
        // fonctionne sur un locataire Microsoft moderne, où l'authentification basique
        // SMTP est désactivée.
        return graph.isConfigured() || notBlank(config.getHost());
    }

    @Override
    public void send(NotificationOutbox notification) {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "Canal courriel non configuré : ni Microsoft Graph "
                            + "(bilanga.notification.email.graph.*) ni SMTP "
                            + "(bilanga.notification.email.host) ne le sont.");
        }
        String recipient = notification.getRecipient();
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Aucune adresse de destinataire.");
        }

        // ------------------------------------------------------------
        // Graph prime sur SMTP quand il est configuré.
        //
        // LE DÉFAUT CORRIGÉ. Ce canal ne parlait que SMTP, avec AUTH LOGIN. Or
        // Microsoft 365 a désactivé l'authentification basique SMTP sur la plupart des
        // locataires : sur un locataire moderne, l'hôte reste vide faute de pouvoir
        // s'authentifier, isAvailable() rend donc faux, et AUCUNE ALERTE NE PART PAR
        // COURRIEL — sans que rien ne le signale, puisqu'un canal indisponible n'est
        // pas un canal en échec.
        //
        // Le service de courrier Graph existait déjà, mais ne servait qu'aux codes de
        // connexion et aux réinitialisations. Les alertes l'ignoraient.
        // ------------------------------------------------------------
        if (graph.isConfigured()) {
            graph.send(recipient, notification.getSubject(), notification.getBody(), false);
            return;
        }

        try (Socket socket = new Socket(config.getHost(), config.getPort())) {
            socket.setSoTimeout(config.getReadTimeoutSeconds() * 1000);

            try (Conversation smtp = new Conversation(socket)) {
                smtp.expect("220");
                smtp.command("EHLO bilanga", "250");

                if (config.isStartTls()) {
                    smtp.command("STARTTLS", "220");
                    smtp.upgradeToTls(config.getHost(), config.getPort());
                    // Après STARTTLS, la session repart de zéro : le serveur a le
                    // droit d'annoncer d'autres capacités sur le canal chiffré, et
                    // certains refusent AUTH avant ce second EHLO.
                    smtp.command("EHLO bilanga", "250");
                }

                if (config.getUsername() != null && !config.getUsername().isBlank()) {
                    smtp.authenticate(config.getUsername(), config.getPassword());
                }

                smtp.command("MAIL FROM:<" + config.getFrom() + ">", "250");
                smtp.command("RCPT TO:<" + recipient.trim() + ">", "250");
                smtp.command("DATA", "354", "250", "250 ", "3");
                smtp.write(messageOf(notification, recipient.trim()));
                smtp.command(".", "250");
                smtp.quietQuit();
            }

            log.debug("Courriel remis à {} pour l'alerte {}.", recipient, notification.getAlertId());

        } catch (IOException e) {
            // Conformément au contrat de NotificationChannel : on lève, la reprise
            // appartient à l'expéditeur. Le message reste EN_ATTENTE et sera
            // retenté au prochain dispatchPending.
            throw new IllegalStateException(
                    "Échec de remise du courriel à " + recipient + " : " + e.getMessage(), e);
        }
    }

    /**
     * Message RFC 5322, en texte brut UTF-8.
     *
     * <p>Le sujet est encodé en Base64 selon la RFC 2047 : il contient de
     * l'urgence traduite en lingala ou en kituba, donc des caractères hors ASCII.
     * Un en-tête non encodé les ferait arriver en mojibake — précisément sur le
     * mot qui décide si le message est ouvert.
     */
    private String messageOf(NotificationOutbox notification, String recipient) {
        String subject = notification.getSubject() == null ? "Bilanga" : notification.getSubject();
        String body = notification.getBody() == null ? "" : notification.getBody();

        return "From: " + config.getFrom() + CRLF
                + "To: " + recipient + CRLF
                + "Subject: " + encodeHeader(subject) + CRLF
                + "Date: " + RFC_5322.format(Instant.now()) + CRLF
                + "MIME-Version: 1.0" + CRLF
                + "Content-Type: text/plain; charset=UTF-8" + CRLF
                + "Content-Transfer-Encoding: 8bit" + CRLF
                + CRLF
                + escapeLeadingDots(body) + CRLF;
    }

    private String encodeHeader(String value) {
        return "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
                + "?=";
    }

    /**
     * Protège les lignes commençant par un point.
     *
     * <p>En SMTP, une ligne réduite à un point termine le corps du message. Un
     * constat agronomique dont une ligne commencerait par « . » tronquerait donc le
     * message à cet endroit — et le serveur répondrait 250, si bien que rien ne
     * signalerait la troncature. Le doublement du point est la parade prévue par le
     * protocole.
     */
    private String escapeLeadingDots(String body) {
        return body.replace("\r\n", "\n")
                .replace("\n.", "\n..");
    }

    // ============================================================
    // Dialogue SMTP
    // ============================================================

    /**
     * Échange minimal avec le serveur, refermé automatiquement.
     *
     * <p>Chaque commande vérifie le code de réponse. Sans cette vérification, un
     * refus du serveur — quota dépassé, destinataire rejeté — passerait inaperçu et
     * la ligne serait marquée comme envoyée alors que rien n'est parti. Un canal qui
     * mentirait sur ses succès serait pire que pas de canal.
     */
    private static final class Conversation implements AutoCloseable {

        private Socket socket;
        private java.io.BufferedReader in;
        private PrintWriter out;

        Conversation(Socket socket) throws IOException {
            bind(socket);
        }

        private void bind(Socket newSocket) throws IOException {
            this.socket = newSocket;
            this.in = new java.io.BufferedReader(
                    new java.io.InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream raw = newSocket.getOutputStream();
            this.out = new PrintWriter(
                    new java.io.OutputStreamWriter(raw, StandardCharsets.UTF_8), true);
        }

        void upgradeToTls(String host, int port) throws IOException {
            javax.net.ssl.SSLSocketFactory factory =
                    (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
            Socket secure = factory.createSocket(socket, host, port, false);
            ((javax.net.ssl.SSLSocket) secure).startHandshake();
            bind(secure);
        }

        void authenticate(String username, String password) throws IOException {
            command("AUTH LOGIN", "334");
            command(base64(username), "334");
            command(base64(password == null ? "" : password), "235");
        }

        private static String base64(String value) {
            return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        void command(String line, String... acceptedPrefixes) throws IOException {
            out.print(line + CRLF);
            out.flush();
            expect(acceptedPrefixes);
        }

        void write(String payload) {
            out.print(payload);
            out.flush();
        }

        void expect(String... acceptedPrefixes) throws IOException {
            String response = readResponse();
            for (String prefix : acceptedPrefixes) {
                if (response.startsWith(prefix)) {
                    return;
                }
            }
            throw new IOException("Réponse SMTP inattendue : " + response);
        }

        /**
         * Lit une réponse, en consommant les lignes de continuation.
         *
         * <p>Un serveur répond à {@code EHLO} par autant de lignes qu'il annonce de
         * capacités, chacune préfixée {@code 250-} sauf la dernière en {@code 250 }.
         * S'arrêter à la première laisserait le reste dans le tampon, et chaque
         * commande suivante lirait la réponse de la précédente — un décalage qui
         * ferait échouer l'envoi sans rapport apparent avec sa cause.
         */
        private String readResponse() throws IOException {
            String line = in.readLine();
            if (line == null) {
                throw new IOException("Le serveur SMTP a fermé la connexion.");
            }
            String first = line;
            while (line != null && line.length() > 3 && line.charAt(3) == '-') {
                line = in.readLine();
            }
            return first;
        }

        /** Un {@code QUIT} refusé n'a aucune conséquence : le message est déjà remis. */
        void quietQuit() {
            try {
                out.print("QUIT" + CRLF);
                out.flush();
            } catch (RuntimeException ignored) {
                // Sans objet.
            }
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // La remise a déjà eu lieu ou échoué ; la fermeture n'ajoute rien.
            }
        }
    }
}
