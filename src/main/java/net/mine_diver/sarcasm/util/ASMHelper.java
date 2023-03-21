package net.mine_diver.sarcasm.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.IntFunction;

public final class ASMHelper {
	private ASMHelper() {}
	
	public static byte[] readClassBytes(Class<?> classObject) {
        byte[] bytes;
        try (InputStream classStream = Objects.requireNonNull(classObject.getClassLoader().getResourceAsStream(classObject.getName().replace('.', '/').concat(".class")))) {
            bytes = new byte[classStream.available()];
            if (classStream.read(bytes) == -1)
                throw new RuntimeException("Couldn't read class \"" + classObject.getName() + "\"!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bytes;
	}

    public static ClassNode readClassNode(Class<?> classObject) {
        ClassNode classNode = new ClassNode();
        new ClassReader(readClassBytes(classObject)).accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }
	
	public static String toTarget(MethodNode method) {
		return method.name + method.desc;
	}

    public static String toTarget(Class<?> owner, MethodNode methodNode) {
        return Type.getDescriptor(owner) + methodNode.name + methodNode.desc;
    }

    public static String toTarget(ClassNode classNode, MethodNode methodNode) {
        return Type.getObjectType(classNode.name).getDescriptor() + methodNode.name + methodNode.desc;
    }

    public static String toTarget(MethodInsnNode methodInsn) {
        return Type.getObjectType(methodInsn.owner).getDescriptor() + methodInsn.name + methodInsn.desc;
    }

    public static String toTarget(Class<?> owner, FieldNode fieldNode) {
        return Type.getDescriptor(owner) + fieldNode.name + ":" + fieldNode.desc;
    }

    public static String toTarget(ClassNode classNode, FieldNode fieldNode) {
        return Type.getObjectType(classNode.name).getDescriptor() + fieldNode.name + ":" + fieldNode.desc;
    }

    public static String toTarget(FieldInsnNode fieldInsn) {
        return Type.getObjectType(fieldInsn.owner).getDescriptor() + fieldInsn.name + ":" + fieldInsn.desc;
    }

    public static LocalVariableNode addLocalVariable(MethodNode method, String desc) {
        return addLocalVariable(method, index -> "var" + index, desc);
    }

    public static LocalVariableNode addLocalVariable(MethodNode method, IntFunction<String> nameFunction, String desc) {
        int index = allocateLocal(method);
        return addLocalVariable(method, index, nameFunction.apply(index), desc);
    }

    /**
     * Allocate a new local variable for the method
     *
     * @return the allocated local index
     */
    public static int allocateLocal(MethodNode method) {
        return allocateLocals(method, 1);
    }

    /**
     * Allocate a number of new local variables for this method, returns the
     * first local variable index of the allocated range.
     *
     * @param method method node
     * @param locals number of locals to allocate
     * @return the first local variable index of the allocated range
     */
    public static int allocateLocals(MethodNode method, int locals) {
        int nextLocal = method.maxLocals;
        method.maxLocals += locals;
        return nextLocal;
    }

    /**
     * Add an entry to the target LVT
     *
     * @param method method node
     * @param index  local variable index
     * @param name   local variable name
     * @param desc   local variable type
     */
    public static LocalVariableNode addLocalVariable(MethodNode method, int index, String name, String desc) {
        return addLocalVariable(method, index, name, desc, null, null);
    }

    /**
     * Add an entry to the target LVT between the specified start and end labels
     *
     * @param method method node
     * @param index  local variable index
     * @param name   local variable name
     * @param desc   local variable type
     * @param from   start of range
     * @param to     end of range
     */
    public static LocalVariableNode addLocalVariable(MethodNode method, int index, String name, String desc, LabelNode from, LabelNode to) {
        if (from == null) {
            from = getStartLabel(method);
        }

        if (to == null) {
            to = getEndLabel(method);
        }

        if (method.localVariables == null) {
            method.localVariables = new ArrayList<>();
        }

        for (Iterator<LocalVariableNode> iter = method.localVariables.iterator(); iter.hasNext();) {
            LocalVariableNode local = iter.next();
            if (local != null && local.index == index && from == local.start && to == local.end) {
                iter.remove();
            }
        }

        LocalVariableNode local = new Locals.SyntheticLocalVariableNode(name, desc, null, from, to, index);
        method.localVariables.add(local);
        return local;
    }

    /**
     * Get a label which marks the very start of the method
     */
    public static LabelNode getStartLabel(MethodNode method) {
        LabelNode start;
        AbstractInsnNode insn = method.instructions.getFirst();
        if (insn.getType() == AbstractInsnNode.LABEL)
            start = (LabelNode) insn;
        else
            method.instructions.insert(start = new LabelNode());
        return start;
    }

    /**
     * Get a label which marks the very end of the method
     */
    public static LabelNode getEndLabel(MethodNode method) {
        LabelNode end;
        AbstractInsnNode insn = method.instructions.getLast();
        if (insn.getType() == AbstractInsnNode.LABEL)
            end = (LabelNode) insn;
        else
            method.instructions.add(end = new LabelNode());
        return end;
    }

    public static MethodNode clone(MethodNode methodNode) {
        MethodNode clonedNode = new MethodNode(methodNode.access, methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(new String[0]));
        methodNode.accept(clonedNode);
        return clonedNode;
    }
}
