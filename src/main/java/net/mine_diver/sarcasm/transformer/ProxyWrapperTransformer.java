package net.mine_diver.sarcasm.transformer;

import net.mine_diver.sarcasm.SarcASM;
import net.mine_diver.sarcasm.util.ASMHelper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static net.mine_diver.sarcasm.util.Util.compose;
import static net.mine_diver.sarcasm.util.Util.soften;
import static org.objectweb.asm.Opcodes.*;

public class ProxyWrapperTransformer<T> implements ProxyTransformer {
    private static final Predicate<MethodNode> NON_STATIC_NON_FINAL_NON_CONSTRUCTOR = methodNode -> !Modifier.isStatic(methodNode.access) && !Modifier.isFinal(methodNode.access) && !"<init>".equals(methodNode.name);
    private static final Predicate<AbstractInsnNode> CONSTRUCTOR = node -> node.getOpcode() == INVOKESPECIAL && "<init>".equals(((MethodInsnNode) node).name);
    private static final Function<MethodInsnNode, InsnList> WRAPPER_FACTORY = constructor -> {
        InsnList wrapper = new InsnList();
        wrapper.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(SarcASM.class), "tryWrapUntrackedProxy", Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(Object.class))));
        wrapper.add(new TypeInsnNode(CHECKCAST, constructor.owner));
        return wrapper;
    };

    private static Predicate<MethodInsnNode> globalConstructorFilter;

    private static final Map<Class<?>, ProxyWrapperTransformer<?>> CACHE = new IdentityHashMap<>();

    public static <T> ProxyWrapperTransformer<T> of(Class<T> targetClass) {
        //noinspection unchecked
        return (ProxyWrapperTransformer<T>) CACHE.computeIfAbsent(targetClass, ProxyWrapperTransformer::new);
    }

    public static void addConstructorFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<MethodInsnNode> filter) {
        globalConstructorFilter = globalConstructorFilter == null ? filter : combiner.apply(globalConstructorFilter, filter);
    }

    public static void addOwnerFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<String> filter) {
        addConstructorFilter(combiner, compose(filter, methodInsnNode -> methodInsnNode.owner));
    }

    public static void addTypeFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<Type> filter) {
        addOwnerFilter(combiner, compose(filter, Type::getObjectType));
    }

    public static void addClassNameFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<String> filter) {
        addTypeFilter(combiner, compose(filter, Type::getClassName));
    }

    public static void addClassFilter(BinaryOperator<Predicate<MethodInsnNode>> combiner, Predicate<Class<?>> filter) {
        addClassNameFilter(combiner, compose(filter, soften(Class::forName)));
    }

    private final String[] methods;

    private ProxyWrapperTransformer(Class<T> targetClass) {
        methods = ASMHelper.readClassNode(targetClass).methods
                .stream()
                .filter(methodNode -> NON_STATIC_NON_FINAL_NON_CONSTRUCTOR.test(methodNode) && StreamSupport
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
                        .forEach(methodInsnNode -> methodNode.instructions.insert(methodInsnNode, WRAPPER_FACTORY.apply(methodInsnNode)))
                );
    }
}
