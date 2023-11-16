package net.mine_diver.sarcasm.transformer;

import net.mine_diver.sarcasm.util.Identifier;
import net.mine_diver.sarcasm.util.collection.toposort.SortableNode;

import java.util.Arrays;

class TransformerPhaseData extends SortableNode<TransformerPhaseData> {
    final Identifier id;
    ProxyTransformer[] transformers;

    TransformerPhaseData(Identifier id) {
        this.id = id;
        transformers = new ProxyTransformer[0];
    }

    void addTransformer(ProxyTransformer transformer) {
        int oldLength = transformers.length;
        transformers = Arrays.copyOf(transformers, oldLength + 1);
        transformers[oldLength] = transformer;
    }

    @Override
    protected String getDescription() {
        return id.toString();
    }
}
