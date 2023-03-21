package net.mine_diver.sarcasm.transformer;

import net.mine_diver.sarcasm.SarcASM;
import net.mine_diver.sarcasm.util.ASMHelper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.stream.Collectors;

public final class RequestedMethodsTransformer<T> implements ProxyTransformer {

    private static final Map<Class<?>, RequestedMethodsTransformer<?>> CACHE = new IdentityHashMap<>();

    public static <T> RequestedMethodsTransformer<T> of(Class<T> targetClass) {
        //noinspection unchecked
        return (RequestedMethodsTransformer<T>) CACHE.computeIfAbsent(targetClass, RequestedMethodsTransformer::new);
    }

    private final Class<T> targetClass;
    private final ClassNode targetNode;

    private RequestedMethodsTransformer(Class<T> targetClass) {
        this.targetClass = targetClass;
        targetNode = ASMHelper.readClassNode(targetClass);
    }

    @Override
    public String[] getRequestedMethods() {
        return new String[0]; // only working with methods that other transformers requested
    }

    @Override
    public void transform(ClassNode node) {
        // initializing requested methods
        final Set<String> requestedMethods = SarcASM.streamTransformers(targetClass).map(stream -> stream.flatMap(transformer -> Arrays.stream(transformer.getRequestedMethods())).collect(Collectors.toSet())).orElseGet(Collections::emptySet);
        if (requestedMethods.isEmpty()) return;
        targetNode.methods.stream().filter(targetMethod -> requestedMethods.remove(ASMHelper.toTarget(targetMethod))).forEach(methodNode -> {
            final MethodNode proxyMethod = ASMHelper.clone(methodNode);
            if (proxyMethod.localVariables.size() > 0)
                proxyMethod.localVariables.get(0).desc = Type.getObjectType(node.name).getDescriptor();
            node.methods.add(proxyMethod);
        });
        if (!requestedMethods.isEmpty())
            throw new IllegalArgumentException("Couldn't find some requested methods for class \"" + targetClass.getName() + "\", such as: " + requestedMethods);
    }
}
