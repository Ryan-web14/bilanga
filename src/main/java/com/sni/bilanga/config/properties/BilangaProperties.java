package com.sni.bilanga.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages métier de Bilanga, en un seul objet typé et validé.
 *
 * <p><strong>Pourquoi cette classe existe.</strong> Les réglages étaient lus par
 * une trentaine d'annotations {@code @Value} disséminées dans autant de classes,
 * chacune portant sa propre valeur par défaut. Rien ne garantissait que la clé
 * écrite dans le fichier de configuration soit celle que le code lisait — et de
 * fait, elles avaient divergé : le fichier déclarait
 * {@code bilanga.risk.ml.base-url} quand le code demandait {@code bilanga.ml.base-url}.
 * Le réglage était donc ignoré, silencieusement, et c'est la valeur codée en dur
 * qui s'appliquait. Un seuil que l'on croit régler et qui ne bouge pas est pire
 * qu'un seuil absent : on cherche l'erreur ailleurs.
 *
 * <p>Avec un objet unique, la structure du fichier <em>est</em> la structure de la
 * classe. Une clé mal placée ne peut plus se cacher : elle ne se lie à rien, et
 * les contraintes ci-dessous font échouer le démarrage plutôt que de laisser
 * tourner un système mal réglé.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "bilanga")
public class BilangaProperties {

    @Valid private final Ml ml = new Ml();
    @Valid private final Ingest ingest = new Ingest();
    @Valid private final Diagnosis diagnosis = new Diagnosis();
    @Valid private final Confidence confidence = new Confidence();
    @Valid private final Risk risk = new Risk();
    @Valid private final Agronomic agronomic = new Agronomic();
    @Valid private final Trend trend = new Trend();
    @Valid private final SensorHealth sensorHealth = new SensorHealth();
    @Valid private final Weather weather = new Weather();
    @Valid private final Neighbourhood neighbourhood = new Neighbourhood();
    @Valid private final Overview overview = new Overview();
    @Valid private final Alert alert = new Alert();
    @Valid private final Notification notification = new Notification();
    @Valid private final Cache cache = new Cache();

    /** Cache des tables de connaissance : Redis partagé, Caffeine local devant. */
    @Data
    public static class Cache {

        /**
         * Durée de vie dans le cache <strong>partagé</strong> (Redis).
         *
         * Sans elle, une modification faite directement en base — au psql ou au
         * pgAdmin, ce qui est le cas courant pour ajuster un seuil — ne serait
         * jamais vue : l'éviction ne se déclenche que sur les écritures passant
         * par l'API. Les moteurs appliqueraient l'ancienne valeur jusqu'au
         * redémarrage, et l'administrateur verrait sa modification enregistrée
         * sans effet sur les diagnostics.
         */
        @Min(1)
        private long knowledgeTtlMinutes = 30;

        /**
         * Durée de vie dans le cache <strong>local</strong> (Caffeine).
         *
         * Volontairement plus courte que celle du cache partagé : rien ne peut
         * vider ce cache depuis l'extérieur. Si une autre instance modifie un
         * seuil, la copie locale de celle-ci ne l'apprendra qu'à l'expiration —
         * cette durée borne donc l'écart possible entre deux instances.
         */
        @Min(1)
        private long localTtlMinutes = 5;

        /**
         * Nombre maximal d'entrées du cache local.
         *
         * Les clés contiennent le nom de la culture, qui vient de la requête :
         * sans borne, interroger avec des cultures arbitraires ferait grossir le
         * cache indéfiniment.
         */
        @Min(1)
        private long knowledgeMaxEntries = 500;
    }

    /** Microservice d'inférence (Python/FastAPI), appelé en REST. */
    @Data
    public static class Ml {

        @NotBlank
        private String baseUrl = "http://localhost:8000";

        /** Court : au-delà, ce n'est pas le calcul qui traîne, c'est le service qui va mal. */
        @Min(1)
        private long connectTimeoutSeconds = 2;

        /** Inférence légère sur huit mesures. */
        @Min(1)
        private long soilTimeoutSeconds = 10;

        /** Réseau convolutif sur une photo : plusieurs secondes sont normales. */
        @Min(1)
        private long visionTimeoutSeconds = 30;

        /** Ne vaut que pour les pannes de transport, jamais pour un refus du service. */
        @Min(1)
        private int maxAttempts = 2;

        @Min(0)
        private long retryBackoffMillis = 250;

        @Valid private final Warmup warmup = new Warmup();

        /**
         * Réveil périodique du microservice d'inférence.
         *
         * <h2>Le problème</h2>
         *
         * <p>Un dyno hébergé s'endort après une période d'inactivité. Le premier appel qui
         * suit paie le démarrage complet — chargement du runtime, des poids des modèles —
         * soit vingt à trente secondes. Or le client d'inférence coupe à
         * {@link #visionTimeoutSeconds} (30 s) et la plateforme coupe elle-même à 30 s :
         * <strong>le premier diagnostic après une mise en veille échoue presque à coup
         * sûr</strong>, et il se lit comme {@code ML_INDISPONIBLE}, c'est-à-dire comme une
         * panne. C'est le pire moment pour ça — une démonstration commence toujours par le
         * premier appel.
         *
         * <h2>Ce que le réveil fait, et ne fait pas</h2>
         *
         * <p>Il n'empêche pas le service de s'endormir : il le réveille <strong>tant que le
         * backend est lui-même actif</strong>. C'est exactement la garantie utile — quelqu'un
         * qui interroge le backend va interroger l'inférence dans la minute. Le tenir éveillé
         * la nuit consommerait des heures de dyno sans que personne n'en profite.
         *
         * <p>Le premier réveil a lieu au démarrage de l'application, sans attendre le premier
         * intervalle : c'est le cas qui compte le plus.
         */
        @Data
        public static class Warmup {

            private boolean enabled = true;

            /**
             * Trente minutes est le seuil de mise en veille usuel. Vingt minutes laisse une
             * marge sans multiplier les appels : trois par heure, sur un point de terminaison
             * qui ne fait que répondre.
             */
            @Min(1)
            private long intervalMinutes = 20;

            /**
             * Généreux, et volontairement : un service endormi met vingt à trente secondes à
             * répondre. Couper avant reviendrait à ne jamais réussir le seul appel qui
             * compte — celui qui réveille.
             */
            @Min(1)
            private long timeoutSeconds = 60;

            /** Point de terminaison sans effet de bord, ajouté à {@code base-url}. */
            @NotBlank
            private String path = "/health";
        }
    }

    /** Réception des relevés émis par le matériel de terrain. */
    @Data
    public static class Ingest {

        /**
         * Clé partagée attendue dans l'en-tête {@code X-Device-Key}.
         *
         * Vide, l'ingestion répond 503 : c'est volontaire, mieux vaut une porte
         * fermée qu'une porte sans serrure. En profil {@code prod}, une clé vide
         * empêche le démarrage (voir {@code ConfigurationGuard}).
         */
        private String deviceKey = "";

        /**
         * Interrupteur d'authentification des boîtiers.
         *
         * <p>À {@code false}, <strong>n'importe quel appelant peut déposer des
         * relevés</strong> : ni clé ni en-tête ne sont exigés. C'est fait pour
         * lever l'obstacle pendant l'intégration du module IoT, quand la clé
         * n'est pas encore câblée côté firmware.
         *
         * <p>Le prix est réel : une mesure fabriquée déclenche un diagnostic, des
         * recommandations et potentiellement une alerte, exactement comme une
         * vraie. Une parcelle peut être polluée par du bruit envoyé depuis
         * n'importe où. À laisser à {@code true} dès que le firmware sait
         * transmettre l'en-tête.
         */
        private boolean requireDeviceKey = true;

        @Valid private final AutoRegister autoRegister = new AutoRegister();

        /**
         * Enregistrement automatique d'un boîtier inconnu, à son premier relevé.
         *
         * <h2>Le manque comblé</h2>
         *
         * <p>Un relevé portant un {@code technicalId} inconnu était refusé en 404
         * {@code DEVICE_NOT_REGISTERED}. C'est juste en exploitation — un boîtier
         * fantôme fausserait le parc — mais c'est un mur en simulation : chaque
         * nouveau montage Wokwi, chaque changement d'identifiant, imposait un
         * passage par {@code POST /devices} avec un jeton d'administration. Le
         * simulateur ne peut pas s'authentifier ; l'exploitant devait donc
         * enregistrer à la main un boîtier qui n'existe pas.
         *
         * <p>Activé, le premier relevé d'un identifiant inconnu <strong>crée</strong>
         * le boîtier et le rattache à une parcelle, puis suit le chemin ordinaire :
         * plausibilité, santé de sonde, diagnostic. Rien d'autre ne change.
         *
         * <p><strong>Ce que cela ouvre, exactement.</strong> Quiconque détient la
         * clé d'ingestion peut créer des boîtiers. Sans cette option il pouvait
         * déjà déposer des relevés sur tout boîtier existant, donc déclencher des
         * diagnostics et des alertes — l'ajout élargit le bruit possible, il
         * n'ouvre pas une porte qui était fermée.
         */
        @Data
        public static class AutoRegister {

            /**
             * Défaut : {@code true}. Le projet est en phase d'intégration IoT et de
             * démonstration ; un simulateur qui se heurte à un 404 sans pouvoir
             * s'authentifier est un obstacle sans contrepartie. À repasser à
             * {@code false} lorsque le parc est stabilisé et que les boîtiers sont
             * enregistrés une fois pour toutes.
             */
            private boolean enabled = true;

            /**
             * Parcelle d'accueil des boîtiers créés automatiquement.
             *
             * <p>Vide, le service retient la parcelle {@code ACTIVE} la plus
             * récemment créée. C'est un choix assumé de commodité : sur une
             * instance de démonstration, c'est presque toujours celle qu'on vient
             * de créer pour l'essai en cours. Le rattachement est journalisé, et
             * reste corrigeable par {@code PUT /devices/{id}}.
             */
            private Long plotId;

            /** Préfixe du nom donné au boîtier créé, suivi de son identifiant technique. */
            private String deviceNamePrefix = "Boîtier auto";
        }
    }

    /** Orchestration du diagnostic. */
    @Data
    public static class Diagnosis {

        /** Délai en deçà duquel un relevé ne relance pas de diagnostic. */
        @Min(0)
        private long minIntervalMinutes = 5;

        @Valid private final Throttle throttle = new Throttle();
        @Valid private final Image image = new Image();
        @Valid private final Threshold threshold = new Threshold();

        @Data
        public static class Throttle {
            /** Désactivable pour une démonstration où l'on veut un diagnostic par relevé. */
            private boolean enabled = true;
        }

        @Data
        public static class Image {
            @Min(1)
            private long maxSizeBytes = 8_388_608L;
        }

        /**
         * Variation minimale d'une mesure qui justifie un nouveau diagnostic
         * avant l'expiration de l'intervalle.
         */
        @Data
        public static class Threshold {
            @DecimalMin("0") private double humidite = 5;
            @DecimalMin("0") private double temperature = 2;
            @DecimalMin("0") private double ph = 0.3;
            @DecimalMin("0") private double nutriment = 5;
            @DecimalMin("0") private double luminosite = 800;
        }
    }

    /** Seuils de confiance des prédictions. */
    @Data
    public static class Confidence {

        @DecimalMin("0") @DecimalMax("1")
        private double high = 0.85;

        /** En deçà, le diagnostic n'est pas jugé fiable et ne lève pas d'alerte. */
        @DecimalMin("0") @DecimalMax("1")
        private double low = 0.60;
    }

    /** Moteur de risque : fraction des conditions d'apparition réunies. */
    @Data
    public static class Risk {

        @DecimalMin("0") @DecimalMax("1")
        private double minScore = 0.60;

        @DecimalMin("0") @DecimalMax("1")
        private double highScore = 0.85;
    }

    /** Moteur agronomique : écart aux exigences de la culture. */
    @Data
    public static class Agronomic {

        /** En deçà, l'écart est trop faible pour mériter un conseil. */
        @DecimalMin("0") @DecimalMax("1")
        private double minSeverity = 0.05;
    }

    @Valid private final Arbitration arbitration = new Arbitration();

    /**
     * Arbitrage des conseils contradictoires.
     *
     * <h2>Le défaut corrigé</h2>
     *
     * <p>{@code ConflictArbitrator} se déclenchait sur la seule <strong>coprésence de
     * catégories</strong> : deux conseils de catégories conciliables suffisaient, quelles
     * que soient les mesures qui les avaient produits.
     *
     * <p>Conséquence : une humidité du sol à 58 % quand la culture en demande 60 produit
     * un conseil de stress hydrique. Combiné à n'importe quel risque sanitaire, il
     * déclenchait une synthèse rédigée comme si <em>les deux</em> problèmes étaient
     * sérieux — alors que les capteurs disaient le contraire de l'un des deux.
     *
     * <p><strong>Le défaut n'était pas d'ajouter, mais d'ajouter trop tôt.</strong>
     * L'invariant « on reformule, on n'efface pas » reste entier : rien n'est retiré, on
     * se contente de ne plus concilier ce qui n'est pas en conflit.
     */
    @Data
    public static class Arbitration {

        /**
         * Écart minimal, <strong>relatif au seuil</strong>, exigé des deux côtés.
         *
         * <p>15 % : en deçà, la mesure est au bord de sa plage et ne constitue pas un
         * problème dont il vaille la peine de discuter la conciliation avec un autre.
         * Au-delà de 30 %, les vrais conflits commenceraient à passer inaperçus.
         *
         * <p>Relatif et non absolu : un écart de 2 sur un pH est considérable, le même
         * sur une concentration d'azote est négligeable. C'est le même raisonnement que
         * celui de {@code SensorHealthAnalyzer}, qui rapporte l'écart à l'étendue observée
         * plutôt qu'à la valeur brute.
         */
        @DecimalMin("0") @DecimalMax("1")
        private double minDeviation = 0.15;

        /**
         * À {@code false}, l'arbitrage retrouve son comportement d'avant — coprésence
         * seule, sans regarder les mesures.
         *
         * <p>Prévu comme filet : si le filtrage appauvrit une démonstration, il se lève
         * par configuration, sans redéploiement ni changement de code.
         */
        private boolean requireSignificantDeviation = true;
    }

    /** Analyse de tendance : régression sur une fenêtre récente. */
    @Data
    public static class Trend {

        @Min(1) private long windowHours = 6;
        @Min(2) private int minPoints = 4;
        @DecimalMin("0.1") private double horizonHours = 12;
        @Min(2) private int maxReadings = 60;

        /** Qualité d'ajustement minimale : en deçà, la projection n'est pas publiée. */
        @DecimalMin("0") @DecimalMax("1")
        private double minRSquared = 0.5;

        /** Pente négligeable, en fraction de l'amplitude observée. */
        @DecimalMin("0") @DecimalMax("1")
        private double negligibleSlopeRatio = 0.01;
    }

    /**
     * Détection de panne de sonde par cohérence.
     *
     * <p>Le contrôle de plausibilité n'attrape que l'absurde. Une sonde en panne
     * reste le plus souvent dans les bornes physiques : elle se fige, elle
     * dérive, elle décroche de ses voisines. C'est le seul angle mort qui puisse
     * produire un conseil nuisible, car la confiance du modèle mesure la
     * certitude de la prédiction et jamais la fiabilité de la mesure.
     */
    @Data
    public static class SensorHealth {

        private boolean enabled = true;

        /**
         * Relevés consécutifs strictement identiques au-delà desquels la sonde
         * est réputée bloquée.
         *
         * Six est un compromis : une valeur figée sur deux ou trois relevés
         * arrive naturellement de nuit, quand rien ne bouge. Au-delà de six, la
         * stabilité parfaite n'est plus un phénomène naturel.
         */
        @Min(3)
        private int stuckReadings = 6;

        /** Profondeur de la fenêtre analysée. */
        @Min(1)
        private long windowHours = 12;

        /** En deçà, la série est trop courte pour conclure quoi que ce soit. */
        @Min(2)
        private int minPoints = 4;

        /** Nombre maximal de relevés rapatriés par boîtier — borne de coût. */
        @Min(2)
        private int maxReadings = 60;

        /**
         * Écart relatif à la médiane des boîtiers voisins au-delà duquel la sonde
         * devient suspecte. 0,25 = 25 % de la valeur de référence.
         */
        @DecimalMin("0") @DecimalMax("10")
        private double driftTolerance = 0.25;

        /**
         * Écart relatif au-delà duquel la sonde est déclarée défaillante et
         * n'alimente plus de diagnostic. Nettement plus haut que la dérive :
         * inhiber le diagnostic est une décision lourde, elle demande davantage
         * qu'un soupçon.
         */
        @DecimalMin("0") @DecimalMax("10")
        private double decouplingTolerance = 0.60;
    }

    /**
     * Prévisions météo (Open-Meteo).
     *
     * <p>Le moteur raisonne sur le passé mesuré ; la prévision est ce qui le
     * fait basculer du constat vers l'anticipation. Open-Meteo est retenu parce
     * qu'il ne demande aucune clé : le système reste démontrable sans compte à
     * gérer, ni abonnement susceptible d'expirer.
     *
     * <p>{@code enabled: false} rend le moteur météo silencieux — il produit une
     * liste vide, exactement comme quand la parcelle n'a pas de coordonnées.
     * C'est la règle appliquée au microservice d'inférence : une capacité
     * indisponible retire une capacité, elle ne casse rien.
     */
    /**
     * Risque de voisinage — le huitième moteur (V27).
     *
     * <p><strong>Ce qu'il apporte.</strong> Le système diagnostiquait une parcelle ;
     * il raisonne désormais sur un territoire. Une maladie détectée chez un voisin
     * proche élève le risque chez soi : la propagation est un fait agronomique que
     * le système ignorait, alors qu'il disposait de tout pour le voir — les
     * coordonnées depuis la V16, l'index géographique depuis la V16, et tous les
     * diagnostics en base depuis l'origine. Il ne manquait que la requête.
     *
     * <p><strong>C'est le seul moteur qui regarde à côté.</strong> Les sept autres
     * examinent la parcelle et son ciel ; celui-ci examine ses voisines. Aucune
     * mesure locale ne peut porter cette information.
     *
     * <p>Même contrat de dégradation que la météo : désactivé, sans coordonnées,
     * ou sans voisin diagnostiqué, il rend une liste vide.
     */
    @Data
    public static class Neighbourhood {

        /**
         * À {@code false}, le moteur rend une liste vide — comme la météo
         * désactivée, comme l'absence de voisin. Une capacité indisponible retire
         * une capacité, elle ne casse rien.
         */
        private boolean enabled = true;

        /**
         * Rayon de recherche, en kilomètres.
         *
         * <p>Deux kilomètres : c'est l'ordre de grandeur sur lequel une maladie
         * foliaire se propage par le vent et par les allées et venues au sein d'un
         * même terroir. Plus large, on relierait des parcelles qui n'ont rien à voir
         * et chaque détection alerterait tout le village ; plus étroit, on ne
         * retiendrait que les parcelles mitoyennes — cas où l'exploitant est déjà au
         * courant par son voisin.
         */
        @DecimalMin("0.1")
        private double radiusKm = 2.0;

        /**
         * Fenêtre de fraîcheur, en jours.
         *
         * <p>Quatorze jours. Un mildiou détecté il y a trois semaines n'annonce plus
         * rien : soit il a été traité, soit la parcelle est perdue, et dans les deux
         * cas la menace de propagation a cessé d'être actuelle. Alerter sur un foyer
         * éteint est le meilleur moyen d'apprendre à l'exploitant que ces conseils ne
         * valent rien.
         */
        @Min(1)
        private int freshnessDays = 14;

        /**
         * Confiance minimale du diagnostic voisin.
         *
         * <p>Aligné sur {@code confidence.low} : un diagnostic non fiable ne lève
         * aucune alerte sur sa propre parcelle, et il serait incohérent qu'il en
         * déclenche une chez le voisin. Une conclusion que le système ne soutient pas
         * ne doit pas devenir un motif de déplacement pour quelqu'un d'autre.
         */
        @DecimalMin("0.0")
        private double minConfidence = 0.60;

        /**
         * Nombre maximal de foyers retenus.
         *
         * <p>Borne de lisibilité autant que de coût : au-delà de trois maladies
         * signalées chez les voisins, le conseil devient une liste qu'on ne lit pas.
         */
        @Min(1)
        private int maxOutbreaks = 3;
    }

    @Data
    public static class Weather {

        private boolean enabled = true;

        @NotBlank
        private String baseUrl = "https://api.open-meteo.com";

        /**
         * Fraîcheur au-delà de laquelle on redemande.
         *
         * Une heure : le fournisseur borne le nombre d'appels, et une prévision
         * datant de moins d'une heure reste juste. Rafraîchir à chaque diagnostic
         * gaspillerait le quota sans rien gagner en précision.
         */
        @Min(1)
        private long cacheTtlMinutes = 60;

        /** Profondeur de prévision exploitée, en heures. */
        @Min(1)
        private long horizonHours = 48;

        /**
         * Pluie cumulée, en mm, à partir de laquelle l'irrigation peut être
         * différée. En deçà, l'averse ne mouille que la surface et ne dispense
         * de rien.
         */
        @DecimalMin("0")
        private double rainThresholdMm = 5;

        /**
         * Délai sous lequel une pluie annoncée rend un traitement inutile — le
         * produit serait lessivé avant d'agir.
         */
        @Min(1)
        private long treatmentRainWindowHours = 6;

        /**
         * Humidité au-delà de laquelle les conditions deviennent favorables aux
         * maladies foliaires ; base de l'alerte préventive.
         */
        @DecimalMin("0") @DecimalMax("100")
        private double highHumidityThreshold = 85;

        @Min(1) private long connectTimeoutSeconds = 3;
        @Min(1) private long readTimeoutSeconds = 10;
        @Min(1) private int maxAttempts = 2;
    }

    /** Tableaux de bord. */
    @Data
    public static class Overview {

        /** Au-delà, un boîtier est réputé silencieux : sa dernière mesure ne fait plus foi. */
        @Min(1)
        private long deviceSilenceMinutes = 15;
    }

    /** Cycle de vie des alertes. */
    @Data
    public static class Alert {

        /** Reconstats sans acquittement au-delà desquels l'alerte monte d'un niveau. */
        @Min(1)
        private int escalationThreshold = 3;
    }

    /** Acheminement des notifications. */
    @Data
    public static class Notification {

        private boolean enabled = true;

        /** Niveau d'alerte minimal justifiant de déranger l'exploitant. */
        @NotNull
        private String minLevel = "ELEVEE";

        @Min(1) private int maxAttempts = 5;
        @Min(1) private int dispatchBatchSize = 20;

        /**
         * Fenêtre de regroupement, en minutes.
         *
         * Les alertes de la même parcelle et du même niveau tombant dans la même
         * tranche sont réunies en un envoi. Cinq alertes en dix minutes doivent
         * faire un message, pas cinq — sans quoi l'exploitant coupe ses
         * notifications et n'apprendra pas non plus la suivante.
         */
        @Min(1)
        private int groupingWindowMinutes = 10;

        @Valid private final Sms sms = new Sms();
        @Valid private final Email email = new Email();
    }

    /**
     * Canal courriel.
     *
     * <p><strong>Pourquoi il vient après le SMS, et non avant.</strong> Le courriel
     * est le canal évident pour un développeur, et le moins utile sur le terrain :
     * il suppose un forfait données, un client de messagerie configuré, et
     * l'habitude de le consulter. Un exploitant au champ lit un SMS dans la minute
     * et un courriel le soir, s'il le lit. Le SMS a donc été livré d'abord.
     *
     * <p>Ce que le courriel apporte, et que le SMS ne peut pas : la place. Un SMS
     * est tronqué à 320 caractères ; le constat agronomique complet, avec ses
     * valeurs mesurées et ses seuils, y entre rarement. C'est le canal du
     * <em>conseiller</em> et du technicien plus que celui de l'exploitant — d'où sa
     * pertinence pour les alertes {@code TECHNIQUE}.
     *
     * <p><strong>Même contrat d'indisponibilité que le SMS</strong> : hôte vide ⇒
     * canal indisponible, rien n'est enfilé, aucun échec compté. C'est l'invariant
     * du projet — une capacité indisponible retire une capacité, elle ne casse rien.
     */
    @Data
    public static class Email {

        private boolean enabled = false;

        /**
         * Hôte SMTP. <strong>Vide ⇒ canal indisponible.</strong> Comme pour le SMS,
         * c'est cette valeur et non {@code enabled} qui décide en dernier ressort :
         * un canal « activé » sans serveur accumulerait des échecs pour rien.
         */
        private String host = "";

        @Min(1)
        private int port = 587;

        private String username = "";
        private String password = "";

        /** Expéditeur annoncé. Certains fournisseurs exigent qu'il corresponde au compte. */
        private String from = "no-reply@bilanga.cg";

        /** STARTTLS : le port 587 l'attend, le 465 attend du TLS implicite. */
        private boolean startTls = true;

        @Min(1)
        private int connectTimeoutSeconds = 5;

        @Min(1)
        private int readTimeoutSeconds = 10;
    }

    /**
     * Passerelle SMS, décrite par la configuration plutôt que par du code.
     *
     * <p><strong>Pourquoi une passerelle générique.</strong> Africa's Talking,
     * Twilio et les passerelles locales exposent toutes la même chose : une URL,
     * un corps JSON contenant un numéro et un texte, un en-tête d'autorisation.
     * Écrire un client par opérateur reviendrait à réécrire trois fois le même
     * appel HTTP, et à devoir livrer du code pour changer de fournisseur — au
     * plus mauvais moment, c'est-à-dire quand l'ancien ne marche plus.
     *
     * <p><strong>Inerte par défaut.</strong> {@code url} vide rend le canal
     * indisponible : {@code NotificationService} ne lui enfile rien et ne compte
     * aucun échec. Le système fonctionne donc sans compte opérateur, ce qui
     * permet de le démontrer sans dépendre d'un abonnement.
     */
    @Data
    public static class Sms {

        private boolean enabled = false;

        /** Vide ⇒ canal indisponible. C'est le seul interrupteur qui compte. */
        private String url = "";

        private String method = "POST";

        /**
         * Gabarit du corps, où {@code {{to}}} et {@code {{body}}} sont substitués.
         *
         * Le texte inséré est échappé pour JSON avant substitution : un message
         * d'alerte contient des guillemets et des accents, qui casseraient
         * sinon la requête.
         */
        private String bodyTemplate = "{\"to\":\"{{to}}\",\"message\":\"{{body}}\"}";

        private String contentType = "application/json";

        /** En-têtes supplémentaires — c'est ici que se met l'autorisation. */
        private final java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();

        /**
         * Numéro ou nom d'expéditeur, substitué par {@code {{from}}} dans le
         * gabarit lorsque l'opérateur l'exige.
         */
        private String sender = "";

        /**
         * Indicatif ajouté aux numéros saisis en forme locale.
         * {@code 06 123 4567} devient {@code +24261234567}.
         */
        private String defaultCountryCode = "+242";

        /**
         * Longueur maximale du message.
         *
         * Un SMS long est facturé plusieurs fois et arrive parfois découpé dans
         * le désordre. Mieux vaut un message tronqué et lisible qu'un message
         * complet reçu en trois morceaux mélangés.
         */
        @Min(40)
        private int maxLength = 320;

        @Min(1) private long connectTimeoutSeconds = 3;
        @Min(1) private long readTimeoutSeconds = 10;
        @Min(1) private int maxAttempts = 2;
    }
}
