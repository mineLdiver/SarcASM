package net.mine_diver.sarcasm.transformer;

import net.mine_diver.sarcasm.SarcASM;
import net.mine_diver.sarcasm.util.ASMHelper;
import net.mine_diver.sarcasm.util.collection.IdentityCache;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static net.mine_diver.sarcasm.util.Util.compose;
import static net.mine_diver.sarcasm.util.Util.soften;
import static org.objectweb.asm.Opcodes.*;

/**
 * Transformer that wraps all constructor calls with a call to {@link SarcASM#tryWrapUntrackedProxy(Object)}.
 *
 * <p>
 *     This is done to expand coverage of SarcASM's transformers
 *     by trying to proxy everything it has access to.
 * </p>
 *
 * <p>
 *     However, it can also cause issues with data structures
 *     that are sensitive to {@link Class} objects,
 *     so it's possible to filter out some classes from being
 *     wrapped by this transformer using {@link #addGlobalConstructorFilter(BinaryOperator, Predicate)},
 *     {@link #addConstructorFilter(BinaryOperator, Predicate)} and their friends.
 * </p>
 *
 * @param <T> type of the class an instance of the transformer is registered for
 */
public class ProxyWrapperTransformer<T> implements ProxyTransformer {
    private static final Predicate<MethodNode> NON_STATIC_NON_FINAL_NON_CONSTRUCTOR = methodNode ->
            !Modifier.isStatic(methodNode.access)
                    && !Modifier.isFinal(methodNode.access)
                    && !"<init>".equals(methodNode.name);
    private static final Predicate<AbstractInsnNode> CONSTRUCTOR = node -> node.getOpcode() == INVOKESPECIAL && "<init>".equals(((MethodInsnNode) node).name);
    private static final Function<MethodInsnNode, InsnList> WRAPPER_FACTORY = constructor -> {
        InsnList wrapper = new InsnList();
        wrapper.add(new MethodInsnNode(
                INVOKESTATIC,
                Type.getInternalName(SarcASM.class),
                "tryWrapUntrackedProxy",
                Type.getMethodDescriptor(
                        Type.getType(Object.class),
                        Type.getType(Object.class)
                )
        ));
        wrapper.add(new TypeInsnNode(CHECKCAST, constructor.owner));
        return wrapper;
    };

    private static Predicate<MethodInsnNode> globalConstructorFilter;

    private static final IdentityCache<Class<?>, ProxyWrapperTransformer<?>> CACHE = new IdentityCache<>(ProxyWrapperTransformer::new);

    public static <T> ProxyWrapperTransformer<T> of(Class<T> targetClass) {
        //noinspection unchecked
        return (ProxyWrapperTransformer<T>) CACHE.get(targetClass);
    }

    public static void addGlobalConstructorFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<MethodInsnNode> filter) {
        globalConstructorFilter = addConstructorFilter(globalConstructorFilter, combiner, filter);
    }

    public static void addGlobalOwnerFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<String> filter) {
        globalConstructorFilter = addOwnerFilter(globalConstructorFilter, combiner, filter);
    }

    public static void addGlobalTypeFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<Type> filter) {
        globalConstructorFilter = addTypeFilter(globalConstructorFilter, combiner, filter);
    }

    public static void addGlobalClassNameFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<String> filter) {
        globalConstructorFilter = addClassNameFilter(globalConstructorFilter, combiner, filter);
    }

    public static void addGlobalClassFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<Class<?>> filter) {
        globalConstructorFilter = addClassFilter(globalConstructorFilter, combiner, filter);
    }

    private static Predicate<MethodInsnNode> addConstructorFilter(
            Predicate<MethodInsnNode> instance,
            BinaryOperator<Predicate<MethodInsnNode>> combiner,
            Predicate<MethodInsnNode> filter
    ) {
        return instance == null ? filter : combiner.apply(instance, filter);
    }

    private static Predicate<MethodInsnNode> addOwnerFilter(
            Predicate<MethodInsnNode> instance,
            BinaryOperator<Predicate<MethodInsnNode>> combiner,
            Predicate<String> filter
    ) {
        return addConstructorFilter(instance, combiner, compose(filter, methodInsnNode -> methodInsnNode.owner));
    }

    private static Predicate<MethodInsnNode> addTypeFilter(
            Predicate<MethodInsnNode> instance,
            BinaryOperator<Predicate<MethodInsnNode>> combiner,
            Predicate<Type> filter
    ) {
        return addOwnerFilter(instance, combiner, compose(filter, Type::getObjectType));
    }

    private static Predicate<MethodInsnNode> addClassNameFilter(
            Predicate<MethodInsnNode> instance,
            BinaryOperator<Predicate<MethodInsnNode>> combiner,
            Predicate<String> filter
    ) {
        return addTypeFilter(instance, combiner, compose(filter, Type::getClassName));
    }

    private static Predicate<MethodInsnNode> addClassFilter(
            Predicate<MethodInsnNode> instance,
            BinaryOperator<Predicate<MethodInsnNode>> combiner,
            Predicate<Class<?>> filter
    ) {
        return addClassNameFilter(instance, combiner, compose(filter, soften(Class::forName)));
    }

    private final String[] methods;
    private Predicate<MethodInsnNode> constructorFilter;

    private ProxyWrapperTransformer(Class<T> targetClass) {
        methods = ASMHelper.readClassNode(targetClass).methods
                .stream()
                .filter(methodNode -> NON_STATIC_NON_FINAL_NON_CONSTRUCTOR.test(methodNode)
                        && StreamSupport
                        .stream(methodNode.instructions.spliterator(), false)
                        .anyMatch(CONSTRUCTOR)
                )
                .map(ASMHelper::toTarget)
                .toArray(String[]::new);
    }

    @Override
    public String[] getRequestedMethods() {
        return methods;
    }

    @Override
    public void transform(ClassNode node) {
        node.methods
                .stream()
                .filter(NON_STATIC_NON_FINAL_NON_CONSTRUCTOR)
                .forEach(methodNode -> StreamSupport
                        .stream(methodNode.instructions.spliterator(), false)
                        .filter(CONSTRUCTOR)
                        .map(insn -> (MethodInsnNode) insn)
                        .filter(globalConstructorFilter == null ? methodInsnNode -> true : globalConstructorFilter)
                        .filter(constructorFilter == null ? methodInsnNode -> true : constructorFilter)
                        .forEach(methodInsnNode -> methodNode.instructions.insert(methodInsnNode, WRAPPER_FACTORY.apply(methodInsnNode)))
                );
    }

    public void addConstructorFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<MethodInsnNode> filter) {
        constructorFilter = addConstructorFilter(constructorFilter, combiner, filter);
    }

    public void addOwnerFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<String> filter) {
        constructorFilter = addOwnerFilter(constructorFilter, combiner, filter);
    }

    public void addTypeFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<Type> filter) {
        constructorFilter = addTypeFilter(constructorFilter, combiner, filter);
    }

    public void addClassNameFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<String> filter) {
        constructorFilter = addClassNameFilter(constructorFilter, combiner, filter);
    }

    public void addClassFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<Class<?>> filter) {
        constructorFilter = addClassFilter(constructorFilter, combiner, filter);
    }
}
