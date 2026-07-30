package com.sni.bilanga.config.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.lang.NonNull;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Cache à deux niveaux : un cache local devant un cache partagé.
 *
 * <p><strong>Pourquoi deux niveaux plutôt qu'un.</strong> Redis seul impose un
 * aller-retour réseau à chaque lecture — pour des tables de quelques dizaines de
 * lignes interrogées plusieurs fois par relevé ingéré, c'est cher payé. Un cache
 * local seul, lui, ne survit ni au redémarrage ni au partage entre instances.
 * Superposer les deux donne la vitesse du local et la persistance du partagé.
 *
 * <p><strong>Et surtout : Redis cesse d'être un point de panne.</strong> Toute
 * défaillance du niveau distant est journalisée puis ignorée, et le niveau local
 * prend le relais. Le pire cas devient une perte de performance, jamais une
 * perte de service — c'est la règle déjà appliquée au microservice d'inférence :
 * une dépendance absente dégrade, elle n'interrompt pas.
 *
 * <p><strong>La contrepartie, à connaître.</strong> Le niveau local n'est pas
 * joignable depuis l'extérieur : si une autre instance vide le cache, la copie
 * locale de celle-ci ne l'apprend pas. C'est la raison pour laquelle sa durée de
 * vie est délibérément plus courte que celle du niveau partagé — elle borne
 * l'écart possible entre deux instances.
 */
@Slf4j
public class TwoLevelCache implements Cache {

    private final String name;
    private final Cache local;
    private final Cache remote;

    /**
     * Prévient les autres instances qu'elles doivent vider leur copie locale.
     * Sans cela, elles continueraient de servir une valeur périmée jusqu'à
     * l'expiration de leur propre cache.
     */
    private final Consumer<String> invalidationBroadcaster;

    public TwoLevelCache(String name, Cache local, Cache remote,
                         Consumer<String> invalidationBroadcaster) {
        this.name = name;
        this.local = local;
        this.remote = remote;
        this.invalidationBroadcaster = invalidationBroadcaster;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public Object getNativeCache() {
        return remote.getNativeCache();
    }

    @Override
    public ValueWrapper get(@NonNull Object key) {
        ValueWrapper cachedLocally = local.get(key);
        if (cachedLocally != null) {
            return cachedLocally;
        }

        ValueWrapper cachedRemotely = remoteGet(key);
        if (cachedRemotely != null) {
            // Remonter la valeur d'un cran : la prochaine lecture évitera le réseau.
            local.put(key, cachedRemotely.get());
        }
        return cachedRemotely;
    }

    @Override
    public <T> T get(@NonNull Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        Object value = wrapper == null ? null : wrapper.get();

        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException(
                    "Valeur en cache du mauvais type pour la clé " + key + " : " + value.getClass());
        }
        return type == null ? (T) value : type.cast(value);
    }

    @Override
    public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            @SuppressWarnings("unchecked")
            T cached = (T) wrapper.get();
            return cached;
        }

        T value;
        try {
            value = valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
        put(key, value);
        return value;
    }

    @Override
    public void put(@NonNull Object key, Object value) {
        local.put(key, value);
        remoteOperation("écriture", () -> remote.put(key, value));
    }

    @Override
    public ValueWrapper putIfAbsent(@NonNull Object key, Object value) {
        ValueWrapper existing = get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(@NonNull Object key) {
        local.evict(key);
        remoteOperation("éviction", () -> remote.evict(key));

        // Le cache distant ne porte pas la notion de « clé évincée ailleurs » :
        // les autres instances vident tout le cache concerné, ce qui est sans
        // conséquence sur des tables de quelques dizaines de lignes.
        invalidationBroadcaster.accept(name);
    }

    @Override
    public void clear() {
        local.clear();
        remoteOperation("purge", remote::clear);
        invalidationBroadcaster.accept(name);
    }

    // ============================================================
    // Interne
    // ============================================================

    private ValueWrapper remoteGet(Object key) {
        try {
            return remote.get(key);
        } catch (RuntimeException e) {
            degrade("lecture", e);
            return null;
        }
    }

    private void remoteOperation(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            degrade(operation, e);
        }
    }

    /**
     * Une éviction perdue est plus gênante qu'une écriture perdue : la valeur
     * partagée reste périmée jusqu'à l'expiration de sa durée de vie. C'est
     * précisément ce que cette durée de vie sert à borner.
     */
    private void degrade(String operation, RuntimeException cause) {
        log.warn("Cache partagé indisponible ({} sur « {} ») : {}. "
                        + "Le cache local prend le relais.",
                operation, name, cause.getMessage());
    }
}
