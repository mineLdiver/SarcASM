package net.mine_diver.sarcasm.util;

import net.mine_diver.sarcasm.util.collection.SoftReferenceCache;

public final class Identifier implements Comparable<Identifier> {
    private static final SoftReferenceCache<IdentifierCacheKey, Identifier> CACHE = new SoftReferenceCache<>(Identifier::new);

    public static final char NAMESPACE_SEPARATOR = ':';

    public static Identifier of(String key) {
        int separatorIndex = key.indexOf(NAMESPACE_SEPARATOR);
        Namespace namespace;
        String path;

        if (separatorIndex == -1) {
            namespace = Namespace.GLOBAL;
            path = key;
        } else if (separatorIndex == key.lastIndexOf(NAMESPACE_SEPARATOR)) {
            namespace = Namespace.of(key.substring(0, separatorIndex));
            path = key.substring(separatorIndex + 1);
        } else throw new IllegalArgumentException("Invalid raw identifier string \"" + key + "\"!");

        return of(namespace, path);
    }


    public static Identifier of(NamespaceProvider provider, String path) {
        return of(Namespace.of(provider), path);
    }

    public static Identifier of(Namespace namespace, String path) {
        return CACHE.get(new IdentifierCacheKey(namespace, path));
    }

    public final Namespace namespace;
    public final String path;
    private final String toString;
    private final int hashCode;

    private Identifier(IdentifierCacheKey key) {
        namespace = key.namespace;
        path = key.path;
        toString = namespace + String.valueOf(NAMESPACE_SEPARATOR) + path;
        hashCode = toString.hashCode();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Identifier && toString.equals(((Identifier) obj).toString))
            throw new IllegalStateException(String.format("Encountered a duplicate instance of Identifier %s!", toString));
        return false;
    }

    @Override
    public String toString() {
        return toString;
    }

    @Override
    public int compareTo(Identifier o) {
        return toString.compareTo(o.toString);
    }

    private static final class IdentifierCacheKey {
        private final Namespace namespace;
        private final String path;
        private final int hashCode;

        private IdentifierCacheKey(Namespace namespace, String path) {
            this.namespace = namespace;
            this.path = path;
            hashCode = 31 * namespace.hashCode() + path.hashCode();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof IdentifierCacheKey)) return false;
            IdentifierCacheKey key = (IdentifierCacheKey) obj;
            return namespace == key.namespace && path.equals(key.path);
        }
    }
}
