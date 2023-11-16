package net.mine_diver.sarcasm.transformer;

import net.mine_diver.sarcasm.SarcASM;
import net.mine_diver.sarcasm.util.Identifier;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface TransformerManager {
    /**
     * The identifier of the default phase.
     */
    Identifier DEFAULT_PHASE = SarcASM.NAMESPACE.id("default");

    static <T> TransformerManager createArrayBacked(Class<T> targetClass, Predicate<Class<?>> hasInjectors) {
        return new ArrayBackedTransformerManager<>(targetClass, hasInjectors);
    }

    default void register(ProxyTransformer transformer) {
        register(DEFAULT_PHASE, transformer);
    }

    default void register(Identifier phaseIdentifier, ProxyTransformer transformer) {
        register(phaseIdentifier, transformer, true);
    }

    void register(Identifier phaseIdentifier, ProxyTransformer transformer, boolean initProxy);

    void forEach(Consumer<ProxyTransformer> consumer);

    Stream<ProxyTransformer> stream();

    void addPhaseOrdering(Identifier firstPhase, Identifier secondPhase);
}
