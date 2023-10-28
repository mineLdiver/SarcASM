package net.mine_diver.sarcasm.util.collection;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

public final class IdentityCache<K, V> {
    private final Map<K, V> cache = new IdentityHashMap<>();
    private final Function<K, V> factory;

    public IdentityCache(Function<K, V> factory) {
        this.factory = factory;
    }

    public V get(K key) {
        return cache.computeIfAbsent(key, factory);
    }
}
