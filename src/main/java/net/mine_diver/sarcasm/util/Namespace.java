package net.mine_diver.sarcasm.util;

import net.mine_diver.sarcasm.util.collection.SoftReferenceCache;

public final class Namespace {
    private static final SoftReferenceCache<String, Namespace> CACHE = new SoftReferenceCache<>(Namespace::new);

    public static final Namespace GLOBAL = of("global");

    public static Namespace of(NamespaceProvider provider) {
        return of(provider.namespace());
    }

    static Namespace of(String namespace) {
        return CACHE.get(namespace);
    }

    public final String namespace;
    private final int hashCode;

    private Namespace(String namespace) {
        this.namespace = namespace;
        hashCode = namespace.hashCode();
    }

    public Identifier id(String id) {
        return Identifier.of(this, id);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Namespace && namespace.equals(((Namespace) obj).namespace))
            throw new IllegalStateException(String.format("Encountered a duplicate instance of Namespace %s!", namespace));
        return false;
    }

    @Override
    public String toString() {
        return namespace;
    }
}
