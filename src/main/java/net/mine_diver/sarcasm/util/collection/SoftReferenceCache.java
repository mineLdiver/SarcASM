package net.mine_diver.sarcasm.util.collection;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class SoftReferenceCache<K, V> {
    private final ConcurrentMap<K, Reference<V>> cache = new ConcurrentHashMap<>();
    private final BiFunction<K, Reference<V>, Reference<V>> referenceValidator;

    public SoftReferenceCache(Function<K, V> factory) {
        final ReferenceQueue<V> queue = new ReferenceQueue<>();
        final class CacheReference extends SoftReference<V> {
            private final K key;

            private CacheReference(V referent, K key) {
                super(referent, queue);
                this.key = key;
            }
        }
        Function<K, Reference<V>> referenceFactory = k -> new CacheReference(factory.apply(k), k);
        referenceValidator = (k, reference) -> reference == null || reference.get() == null ? referenceFactory.apply(k) : reference;
        new Thread(() -> {
            while (true) try {
                //noinspection unchecked
                cache.remove(((CacheReference) queue.remove()).key);
            } catch (InterruptedException ignored) {}
        }).start();
    }

    public V get(K key) {
        return cache.compute(key, referenceValidator).get();
    }
}
