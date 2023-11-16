package net.mine_diver.sarcasm.transformer;

import net.mine_diver.sarcasm.util.Identifier;
import net.mine_diver.sarcasm.util.collection.toposort.NodeSorting;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static net.mine_diver.sarcasm.SarcASM.initProxyFor;
import static net.mine_diver.sarcasm.SarcASM.invalidateProxyClass;

class ArrayBackedTransformerManager<T> implements TransformerManager {
    private final Object lock = new Object();
    private ProxyTransformer[] transformers = new ProxyTransformer[0];

    private final Map<Identifier, TransformerPhaseData> phases = new IdentityHashMap<>();
    private final List<TransformerPhaseData> sortedPhases = new ArrayList<>();

    private final Class<T> targetClass;
    private final BooleanSupplier hasInjectors;

    ArrayBackedTransformerManager(Class<T> targetClass, Predicate<Class<?>> hasInjectors) {
        this.targetClass = targetClass;
        this.hasInjectors = () -> hasInjectors.test(targetClass);
    }

    @Override
    public void register(Identifier phaseIdentifier, ProxyTransformer transformer, boolean initProxy) {
        Objects.requireNonNull(phaseIdentifier, "Tried to register a transformer for a null phase!");
        Objects.requireNonNull(transformer, "Tried to register a null transformer!");

        synchronized (lock) {
            getOrCreatePhase(phaseIdentifier, true).addTransformer(transformer);
            rebuildInvoker(transformers.length + 1);

            invalidateProxyClass(targetClass);
            if (initProxy && hasInjectors.getAsBoolean())
                initProxyFor(targetClass);
        }
    }

    @Override
    public void forEach(Consumer<ProxyTransformer> consumer) {
        for (ProxyTransformer transformer : transformers) consumer.accept(transformer);
    }

    @Override
    public Stream<ProxyTransformer> stream() {
        return Arrays.stream(transformers);
    }

    private TransformerPhaseData getOrCreatePhase(Identifier id, boolean sortIfCreate) {
        TransformerPhaseData phase = phases.get(id);

        if (phase == null) {
            phase = new TransformerPhaseData(id);
            phases.put(id, phase);
            sortedPhases.add(phase);

            if (sortIfCreate) {
                NodeSorting.sort(sortedPhases, "transformer phases", Comparator.comparing(data -> data.id));
            }
        }

        return phase;
    }

    private void rebuildInvoker(int newLength) {
        // Rebuild transformers.
        if (sortedPhases.size() == 1) {
            // Special case with a single phase: use the array of the phase directly.
            transformers = sortedPhases.get(0).transformers;
        } else {
            ProxyTransformer[] newTransformers = new ProxyTransformer[newLength];
            int newTransformersIndex = 0;

            for (TransformerPhaseData existingPhase : sortedPhases) {
                int length = existingPhase.transformers.length;
                System.arraycopy(existingPhase.transformers, 0, newTransformers, newTransformersIndex, length);
                newTransformersIndex += length;
            }

            transformers = newTransformers;
        }
    }

    @Override
    public void addPhaseOrdering(Identifier firstPhase, Identifier secondPhase) {
        Objects.requireNonNull(firstPhase, "Tried to add an ordering for a null phase.");
        Objects.requireNonNull(secondPhase, "Tried to add an ordering for a null phase.");
        if (firstPhase.equals(secondPhase)) throw new IllegalArgumentException("Tried to add a phase that depends on itself.");

        synchronized (lock) {
            TransformerPhaseData first = getOrCreatePhase(firstPhase, false);
            TransformerPhaseData second = getOrCreatePhase(secondPhase, false);
            TransformerPhaseData.link(first, second);
            NodeSorting.sort(this.sortedPhases, "transformer phases", Comparator.comparing(data -> data.id));
            rebuildInvoker(transformers.length);
        }
    }
}
