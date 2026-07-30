package com.sni.bilanga.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sni.bilanga.config.cache.CacheInvalidationBroadcaster;
import com.sni.bilanga.config.cache.TwoLevelCacheManager;
import com.sni.bilanga.config.properties.BilangaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Cache des tables de connaissance : Redis partagé, Caffeine local devant.
 *
 * <p><strong>Ce qu'on évite.</strong> Chaque diagnostic interroge cinq moteurs,
 * qui relisent tous la base de connaissance : exigences de la culture, seuils du
 * stade, règles, maladies, conditions de risque, corrélations, arbitrages. Ces
 * tables comptent quelques dizaines de lignes et ne changent qu'à l'initiative
 * d'un administrateur — elles étaient pourtant relues intégralement à chaque
 * relevé ingéré.
 *
 * <p><strong>Sérialisation en JSON, et non en Java.</strong> C'est ce qui avait
 * fait échouer la première tentative : le sérialiseur par défaut de Spring Data
 * Redis repose sur la sérialisation Java, laquelle exige {@code Serializable}.
 * Les entités JPA ne l'implémentent pas, et toute lecture mise en cache échouait
 * en {@code NotSerializableException}. Les rendre {@code Serializable} aurait
 * été le mauvais remède : on aurait figé la représentation binaire du modèle
 * dans Redis, avec des entrées devenant illisibles à la moindre évolution du
 * schéma. Le JSON est tolérant à l'ajout d'un champ, et lisible depuis
 * {@code redis-cli}.
 *
 * <p>Les sept entités mises en cache sont plates — aucune association vers une
 * autre entité — ce qui écarte le piège habituel du cache d'entités JPA : la
 * sérialisation d'un proxy paresseux.
 *
 * <p><strong>Les deux durées de vie ne sont pas les mêmes, et c'est voulu.</strong>
 * Le niveau partagé garde ses entrées longtemps : c'est la référence, et
 * l'éviction déclenchée par l'API le tient à jour. Le niveau local expire
 * beaucoup plus vite, parce que <em>rien ne peut le vider depuis l'extérieur</em> :
 * si une autre instance modifie un seuil, la copie locale de celle-ci ne
 * l'apprendra qu'à l'expiration. Sa durée de vie borne donc l'écart possible
 * entre deux instances.
 *
 * <p><strong>Invalidation.</strong> Toute écriture par l'API vide les deux
 * niveaux ({@link EvictsKnowledgeCaches}). Les durées de vie couvrent le cas
 * qu'aucune éviction ne peut voir : une modification faite directement en base,
 * au psql ou au pgAdmin, qui ne passe par aucun service.
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig implements CachingConfigurer, SmartInitializingSingleton {

    /** Exigences agronomiques générales, par culture. */
    public static final String CROP_REQUIREMENTS = "knowledge.cropRequirements";

    /** Infléchissements par stade de croissance. */
    public static final String STAGE_REQUIREMENTS = "knowledge.stageRequirements";

    /** Règles attachées à un diagnostic capteur. */
    public static final String RULES = "knowledge.rules";

    /** Fiches maladie. */
    public static final String DISEASES = "knowledge.diseases";

    /** Conditions d'apparition pondérées. */
    public static final String RISK_CONDITIONS = "knowledge.riskConditions";

    /** Corrélations maladie / mesures. */
    public static final String CORRELATIONS = "knowledge.correlations";

    /** Arbitrages entre conseils contradictoires. */
    public static final String ARBITRATIONS = "knowledge.arbitrations";

    /** Tous les caches de la base de connaissance. */
    public static final String[] ALL_KNOWLEDGE = {
            CROP_REQUIREMENTS, STAGE_REQUIREMENTS, RULES,
            DISEASES, RISK_CONDITIONS, CORRELATIONS, ARBITRATIONS
    };

    /**
     * Préfixe des clés. Redis est souvent partagé entre applications ; sans
     * préfixe, deux services aux caches homonymes se marcheraient dessus.
     */
    private static final String KEY_PREFIX = "bilanga:cache:";

    /** Délai de reconnexion de l'abonnement après une coupure. */
    private static final Duration RECOVERY_INTERVAL = Duration.ofSeconds(30);

    private final RedisConnectionFactory redisConnectionFactory;
    private final BilangaProperties properties;
    private final ObjectProvider<CacheInvalidationBroadcaster> invalidationBroadcaster;
    private final ObjectProvider<RedisMessageListenerContainer> invalidationListener;

    @Bean
    @Override
    public CacheManager cacheManager() {
        BilangaProperties.Cache config = properties.getCache();

        log.info("Cache de la base de connaissance : Redis ({} min) avec repli local Caffeine ({} min), "
                        + "invalidation propagée entre instances",
                config.getKnowledgeTtlMinutes(), config.getLocalTtlMinutes());

        return new TwoLevelCacheManager(
                localCacheManager(),
                sharedCacheManager(config),
                List.of(ALL_KNOWLEDGE),
                invalidationBroadcaster);
    }

    /**
     * Diffuseur d'invalidation, partagé entre le gestionnaire et l'abonnement
     * Redis. Résolu paresseusement : il dépend du cache local, que le
     * gestionnaire assemble.
     */
    @Bean
    public CacheInvalidationBroadcaster cacheInvalidationBroadcaster(
            ObjectProvider<StringRedisTemplate> redisTemplate) {
        return new CacheInvalidationBroadcaster(redisTemplate, localCacheManager());
    }

    /**
     * Abonnement au canal d'invalidation.
     *
     * <p>Sans lui, une instance publierait ses invalidations sans jamais recevoir
     * celles des autres — le défaut ne se verrait qu'à partir du deuxième
     * processus en service, c'est-à-dire en production.
     *
     * <p><strong>Le démarrage automatique est désactivé volontairement.</strong>
     * Ce conteneur ouvre sa connexion pendant l'initialisation du contexte : si
     * Redis est injoignable à cet instant, il fait échouer le démarrage de toute
     * l'application. Ce serait contredire la règle qu'on s'est donnée — une
     * dépendance absente dégrade le service, elle ne l'interrompt pas — et
     * rendre le cache plus fragile que l'absence de cache.
     *
     * <p>{@code recoveryInterval} assure la reconnexion en cas de coupure
     * ultérieure, une fois le conteneur démarré.
     */
    @Bean
    public RedisMessageListenerContainer cacheInvalidationListener(
            RedisConnectionFactory connectionFactory,
            CacheInvalidationBroadcaster broadcaster) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(broadcaster,
                new ChannelTopic(CacheInvalidationBroadcaster.CHANNEL));

        container.setAutoStartup(false);
        container.setRecoveryInterval(RECOVERY_INTERVAL.toMillis());
        return container;
    }

    /**
     * Démarre l'abonnement une fois tous les singletons construits, sans pouvoir
     * faire échouer le démarrage.
     *
     * <p>{@code SmartInitializingSingleton} plutôt que {@code ApplicationReadyEvent} :
     * ce dernier n'est pas publié dans un contexte de test, si bien que
     * l'abonnement ne démarrait jamais et qu'aucun test n'aurait pu s'en
     * apercevoir. Un mécanisme qui ne s'exécute qu'en production est un
     * mécanisme dont on ne sait rien.
     *
     * <p>Si Redis est absent au démarrage, {@code start()} ne lève rien : le
     * conteneur retente l'abonnement toutes les {@code RECOVERY_INTERVAL} et
     * s'attache dès que Redis revient. Entre-temps, c'est la durée de vie du
     * cache local qui borne l'écart entre instances — exactement comme avant
     * l'ajout de ce canal. Le {@code catch} ci-dessous ne couvre donc qu'un
     * échec immédiat et inattendu ; il est là pour garantir qu'aucun cas ne
     * puisse faire échouer le démarrage.
     */
    @Override
    public void afterSingletonsInstantiated() {
        RedisMessageListenerContainer container = invalidationListener.getIfAvailable();
        if (container == null || container.isRunning()) {
            return;
        }
        try {
            container.start();
            log.info("Invalidation de cache entre instances : à l'écoute sur « {} »",
                    CacheInvalidationBroadcaster.CHANNEL);
        } catch (RuntimeException e) {
            log.warn("Invalidation entre instances indisponible ({}). "
                            + "La durée de vie du cache local ({} min) reste le filet de sécurité.",
                    e.getMessage(), properties.getCache().getLocalTtlMinutes());
        }
    }

    /**
     * Niveau local : rapide, et seul debout si Redis tombe. Sa taille est bornée
     * parce que les clés contiennent le nom de la culture, qui vient de la
     * requête — sans borne, des cultures arbitraires feraient grossir le cache
     * indéfiniment.
     *
     * Exposé en bean pour que le diffuseur d'invalidation vide exactement les
     * mêmes caches que ceux servis aux moteurs.
     */
    @Bean
    public CacheManager localCacheManager() {
        BilangaProperties.Cache config = properties.getCache();
        CaffeineCacheManager manager = new CaffeineCacheManager(ALL_KNOWLEDGE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(config.getLocalTtlMinutes()))
                .maximumSize(config.getKnowledgeMaxEntries())
                .recordStats());
        return manager;
    }

    /** Niveau partagé : la référence, qui survit au redémarrage de l'application. */
    private CacheManager sharedCacheManager(BilangaProperties.Cache config) {
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(config.getKnowledgeTtlMinutes()))
                .prefixCacheNameWith(KEY_PREFIX)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJacksonJsonRedisSerializer(cacheObjectMapper())));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfiguration)
                // Nommés à l'avance : une faute de frappe dans un @Cacheable
                // créerait sinon un cache silencieusement inutile.
                .initialCacheNames(Set.of(ALL_KNOWLEDGE))
                .build();
    }

    /**
     * Convertisseur JSON du cache partagé.
     *
     * <p>Le typage par défaut est nécessaire : sans lui, une valeur relue depuis
     * Redis serait rendue sous forme de {@code LinkedHashMap} au lieu de son type
     * d'origine, et les moteurs recevraient des objets inutilisables. Il inscrit
     * le nom de la classe dans le document afin de la reconstruire.
     *
     * <p><strong>Restreint volontairement.</strong> Autoriser le typage sans
     * filtre est la faille de désérialisation classique : quiconque peut écrire
     * dans Redis fait alors instancier une classe arbitraire au démarrage de la
     * lecture. Le validateur n'accepte donc que les types du projet et les
     * collections standard — tout le reste est refusé, même si une entrée
     * malveillante parvenait jusqu'au cache.
     */
    private ObjectMapper cacheObjectMapper() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.sni.bilanga.")
                .allowIfSubType("java.util.")
                .build();

        return JsonMapper.builder()
                .activateDefaultTyping(typeValidator, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
                .build();
    }
}
