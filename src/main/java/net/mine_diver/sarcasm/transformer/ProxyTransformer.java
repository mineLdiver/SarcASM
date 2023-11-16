package net.mine_diver.sarcasm.transformer;

import net.mine_diver.sarcasm.util.ASMHelper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public interface ProxyTransformer {
    /**
     * Proxy transformers are required to provide the method targets
     * they're going to transform beforehand, because SarcASM has to
     * paste the methods into the generated proxy class.
     * Remember, you're not transforming the target class directly,
     * you're transforming a generated child class of the target class.
     *
     * @return an array of the requested method's targets
     * @see ASMHelper#toTarget(MethodNode)
     */
    String[] getRequestedMethods();

    /**
     * The transformers are invoked each time a new transformer/injector
     * for the target class is registered, so the transformation code
     * has to be deterministic.
     *
     * @param node the proxy class node
     */
    void transform(ClassNode node);
}
