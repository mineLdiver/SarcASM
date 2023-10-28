package net.mine_diver.sarcasm.transformer;

import net.mine_diver.sarcasm.util.ASMHelper;
import net.mine_diver.sarcasm.util.Util;
import net.mine_diver.sarcasm.util.collection.IdentityCache;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.objectweb.asm.Opcodes.*;

/**
 * Transforms all "super" calls in the proxy class
 * to a method handle hack that calls "super.super",
 * allowing to access members of the target's super class.
 *
 * @param <T> type of the class an instance of the transformer is registered for
 */
public final class SuperSuperTransformer<T> implements ProxyTransformer {
    private static final IdentityCache<Class<?>, SuperSuperTransformer<?>> CACHE = new IdentityCache<>(SuperSuperTransformer::new);

    public static <T> SuperSuperTransformer<T> of(Class<T> targetClass) {
        //noinspection unchecked
        return (SuperSuperTransformer<T>) CACHE.get(targetClass);
    }

    private final ClassNode targetNode;

    private SuperSuperTransformer(Class<T> targetClass) {
        targetNode = ASMHelper.readClassNode(targetClass);
    }

    @Override
    public String[] getRequestedMethods() {
        return new String[0]; // only working with methods that other transformers requested
    }

    @Override
    public void transform(ClassNode node) {
        // super.super implementation
        final Map<String, FieldNode> methodHandles = new HashMap<>();
        new ArrayList<>(node.methods)
                .forEach(methodNode -> {
                    StreamSupport
                            .stream(methodNode.instructions.spliterator(), false)
                            .filter(abstractInsnNode -> abstractInsnNode.getType() == AbstractInsnNode.METHOD_INSN)
                            .map(abstractInsnNode -> ((MethodInsnNode) abstractInsnNode))
                            .filter(methodInsnNode -> methodInsnNode.owner.equals(targetNode.superName)
                                    && targetNode.methods
                                    .stream()
                                    .anyMatch(methodNode1 -> methodInsnNode.name.equals(methodNode1.name)
                                            && methodInsnNode.desc.equals(methodNode1.desc))
                            )
                            .forEach(methodInsnNode -> {
                                final String target = ASMHelper.toTarget(methodInsnNode);
                                if (!methodHandles.containsKey(target)) {
                                    final FieldNode methodHandle = new FieldNode(
                                            ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                                            "SARCASM$super_" + methodInsnNode.name,
                                            Type.getDescriptor(MethodHandle.class),
                                            null, null
                                    );
                                    methodHandles.put(target, methodHandle);
                                    node.fields.add(methodHandle);
                                    final InsnList mhInit = new InsnList();
                                    mhInit.add(new FieldInsnNode(
                                            GETSTATIC,
                                            Type.getInternalName(Util.class),
                                            "IMPL_LOOKUP",
                                            Type.getDescriptor(MethodHandles.Lookup.class))
                                    );
                                    mhInit.add(new LdcInsnNode(Type.getObjectType(targetNode.superName)));
                                    mhInit.add(new LdcInsnNode(methodInsnNode.name));
                                    mhInit.add(new LdcInsnNode(Type.getMethodType(methodInsnNode.desc)));
                                    mhInit.add(new LdcInsnNode(Type.getObjectType(targetNode.name)));
                                    mhInit.add(new MethodInsnNode(
                                            INVOKEVIRTUAL,
                                            Type.getInternalName(MethodHandles.Lookup.class),
                                            "findSpecial",
                                            Type.getMethodDescriptor(
                                                    Type.getType(MethodHandle.class),
                                                    Type.getType(Class.class),
                                                    Type.getType(String.class),
                                                    Type.getType(MethodType.class),
                                                    Type.getType(Class.class)
                                            )
                                    ));
                                    mhInit.add(new FieldInsnNode(PUTSTATIC, node.name, methodHandle.name, methodHandle.desc));
                                    final InsnList clinitInsns = node.methods
                                            .stream()
                                            .filter(methodNode1 -> methodNode1.name.equals("<clinit>"))
                                            .findFirst()
                                            .orElseGet(() -> {
                                                final MethodNode clinit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
                                                clinit.instructions.add(new InsnNode(RETURN));
                                                node.methods.add(clinit);
                                                return clinit;
                                            }).instructions;
                                    clinitInsns.insertBefore(clinitInsns.getLast(), mhInit);
                                }
                                final FieldNode methodHandle = methodHandles.get(target);
                                final InsnList superCall = new InsnList();
                                final LabelNode startTry = new LabelNode();
                                superCall.add(startTry);
                                final Type[] argumentTypes = Util.concat(Type.getObjectType(node.name), Type.getArgumentTypes(methodInsnNode.desc));
                                int curArg = methodNode.maxLocals;
                                for (final Type argumentType : argumentTypes)
                                    ASMHelper.addLocalVariable(methodNode, argumentType.getDescriptor());
                                for (int i = argumentTypes.length - 1; i >= 0; i--)
                                    superCall.add(new VarInsnNode(argumentTypes[i].getOpcode(ISTORE), methodNode.maxLocals - argumentTypes.length + i));
                                superCall.add(new FieldInsnNode(GETSTATIC, node.name, methodHandle.name, methodHandle.desc));
                                for (Type argumentType : argumentTypes)
                                    superCall.add(new VarInsnNode(argumentType.getOpcode(ILOAD), curArg++));
                                superCall.add(new MethodInsnNode(
                                        INVOKEVIRTUAL,
                                        Type.getInternalName(MethodHandle.class),
                                        "invoke",
                                        Type.getMethodDescriptor(
                                                Type.getReturnType(methodInsnNode.desc),
                                                argumentTypes
                                        )
                                ));
                                final LabelNode endTry = new LabelNode();
                                superCall.add(endTry);
                                final LabelNode exitTry = new LabelNode();
                                superCall.add(new JumpInsnNode(GOTO, exitTry));
                                final LabelNode handleTry = new LabelNode();
                                superCall.add(handleTry);
                                final LocalVariableNode throwable = ASMHelper.addLocalVariable(
                                        methodNode,
                                        ASMHelper.allocateLocal(methodNode),
                                        "e",
                                        Type.getDescriptor(Throwable.class),
                                        handleTry,
                                        exitTry
                                );
                                superCall.add(new VarInsnNode(ASTORE, throwable.index));
                                superCall.add(new TypeInsnNode(NEW, Type.getInternalName(RuntimeException.class)));
                                superCall.add(new InsnNode(DUP));
                                superCall.add(new VarInsnNode(ALOAD, throwable.index));
                                superCall.add(new MethodInsnNode(
                                        INVOKESPECIAL,
                                        Type.getInternalName(RuntimeException.class),
                                        "<init>",
                                        Type.getMethodDescriptor(
                                                Type.VOID_TYPE,
                                                Type.getType(Throwable.class)
                                        )
                                ));
                                superCall.add(new InsnNode(ATHROW));
                                superCall.add(exitTry);
                                methodNode.instructions.insertBefore(methodInsnNode, superCall);
                                methodNode.instructions.remove(methodInsnNode);
                                methodNode.tryCatchBlocks.add(new TryCatchBlockNode(startTry, endTry, handleTry, Type.getInternalName(Throwable.class)));
                            });
                    if (methodNode.localVariables.size() > 0)
                        methodNode.localVariables.get(0).start = ASMHelper.getStartLabel(methodNode);
                });
        if (!methodHandles.isEmpty())
            node.innerClasses.add(new InnerClassNode(
                    "java/lang/invoke/MethodHandles$Lookup",
                    "java/lang/invoke/MethodHandles",
                    "Lookup",
                    ACC_PUBLIC | ACC_STATIC | ACC_FINAL
            ));
    }
}
