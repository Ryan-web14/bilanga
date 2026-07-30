package com.sni.bilanga.config.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Propage les invalidations de cache aux autres instances.
 *
 * <p><strong>Le problème.</strong> Le cache local (Caffeine) n'est joignable que
 * depuis l'instance qui le détient. Lorsqu'un administrateur corrige un seuil
 * agronomique, l'éviction vide le cache local de l'instance qui a traité la
 * requête, ainsi que le cache partagé — mais les <em>autres</em> instances
 * gardent leur copie et continuent d'appliquer l'ancienne valeur jusqu'à
 * expiration.
 *
 * <p>Sur un seul serveur, cela ne se voit pas. Dès qu'on met en service un
 * deuxième processus — et c'est la façon normale de monter en charge sur une
 * plateforme comme Heroku — deux requêtes identiques peuvent donner deux
 * diagnostics différents selon l'instance qui répond. Un écart d'autant plus
 * déroutant qu'il est intermittent.
 *
 * <p><strong>La parade.</strong> Un canal de diffusion Redis. L'instance qui
 * évince publie le nom du cache concerné ; les autres reçoivent le message et
 * vident leur copie locale. Redis servant déjà de cache partagé, cela n'ajoute
 * aucune infrastructure.
 *
 * <p>Chaque message porte l'identifiant de son émetteur : sans cela, l'instance
 * qui publie recevrait son propre message et viderait une seconde fois un cache
 * qu'elle vient de vider — inoffensif mais inutile, et trompeur à la lecture des
 * journaux.
 *
 * <p><strong>Une diffusion perdue n'est pas une erreur fatale.</strong> Si Redis
 * est injoignable, la publication échoue en silence et l'on retombe sur le
 * comportement précédent : les autres instances se remettront à jour à
 * l'expiration de leur cache local. C'est précisément le rôle de cette durée de
 * vie, qui reste le filet de sécurité.
 */
@Slf4j
public class CacheInvalidationBroadcaster implements MessageListener {

    /** Canal de diffusion. Préfixé comme les clés : Redis peut être partagé. */
    public static final String CHANNEL = "bilanga:cache:invalidate";

    private static final String ALL_CACHES = "*";
    private static final String SEPARATOR = "|";

    /**
     * Identité de cette instance, tirée au démarrage. Sert uniquement à ignorer
     * ses propres messages.
     */
    private final String instanceId = UUID.randomUUID().toString();

    private final ObjectProvider<StringRedisTemplate> redisTemplate;
    private final CacheManager localCacheManager;

    public CacheInvalidationBroadcaster(ObjectProvider<StringRedisTemplate> redisTemplate,
                                        CacheManager localCacheManager) {
        this.redisTemplate = redisTemplate;
        this.localCacheManager = localCacheManager;
    }

    /** Annonce aux autres instances que ce cache doit être vidé chez elles. */
    public void broadcast(String cacheName) {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        if (template == null) {
            return;
        }
        try {
            template.convertAndSend(CHANNEL, instanceId + SEPARATOR
                    + (cacheName == null ? ALL_CACHES : cacheName));
        } catch (RuntimeException e) {
            // La durée de vie du cache local reste le filet : les autres
            // instances se remettront à jour d'elles-mêmes.
            log.warn("Diffusion de l'invalidation impossible ({}) : {}",
                    cacheName, e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        int separator = payload.indexOf(SEPARATOR);
        if (separator < 0) {
            return;
        }

        String origin = payload.substring(0, separator);
        String cacheName = payload.substring(separator + 1);

        // Notre propre message : le cache local a déjà été vidé sur place.
        if (instanceId.equals(origin)) {
            return;
        }

        if (ALL_CACHES.equals(cacheName)) {
            localCacheManager.getCacheNames().forEach(this::clearLocal);
        } else {
            clearLocal(cacheName);
        }
        log.debug("Cache local « {} » vidé sur invalidation d'une autre instance", cacheName);
    }

    private void clearLocal(String cacheName) {
        Cache cache = localCacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
