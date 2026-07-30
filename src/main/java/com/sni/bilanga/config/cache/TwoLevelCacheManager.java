package com.sni.bilanga.config.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assemble, pour chaque nom de cache, le niveau local et le niveau partagé.
 *
 * <p>Les deux gestionnaires sous-jacents restent responsables de leurs propres
 * réglages — durée de vie, taille, sérialisation. Celui-ci ne fait que les
 * apparier et présenter le résultat comme un cache unique à Spring.
 */
public class TwoLevelCacheManager implements CacheManager {

    private final CacheManager local;
    private final CacheManager remote;
    private final Collection<String> cacheNames;

    /**
     * Résolu à l'appel et non à la construction : le diffuseur dépend du cache
     * local, que ce gestionnaire assemble — les résoudre l'un par l'autre
     * créerait un cycle au démarrage.
     */
    private final ObjectProvider<CacheInvalidationBroadcaster> broadcaster;

    /** Les caches assemblés sont conservés : en recréer un à chaque appel viderait le niveau local. */
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    public TwoLevelCacheManager(CacheManager local, CacheManager remote,
                                Collection<String> cacheNames,
                                ObjectProvider<CacheInvalidationBroadcaster> broadcaster) {
        this.local = local;
        this.remote = remote;
        this.cacheNames = cacheNames;
        this.broadcaster = broadcaster;
    }

    @Override
    public Cache getCache(@NonNull String name) {
        return caches.computeIfAbsent(name, key -> {
            Cache localCache = local.getCache(key);
            Cache remoteCache = remote.getCache(key);

            if (localCache == null || remoteCache == null) {
                // Un nom inconnu des deux gestionnaires ne doit pas provoquer
                // d'erreur silencieuse : mieux vaut le cache disponible, ou rien.
                return localCache != null ? localCache : remoteCache;
            }
            return new TwoLevelCache(key, localCache, remoteCache, this::broadcastInvalidation);
        });
    }

    @Override
    @NonNull
    public Collection<String> getCacheNames() {
        return cacheNames;
    }

    private void broadcastInvalidation(String cacheName) {
        CacheInvalidationBroadcaster publisher = broadcaster.getIfAvailable();
        if (publisher != null) {
            publisher.broadcast(cacheName);
        }
    }
}
