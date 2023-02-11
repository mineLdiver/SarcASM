package net.mine_diver.sarcasm.util;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class ASMHelper {
	private ASMHelper() {}
	
	public static byte[] readClassBytes(Class<?> classObject) {
        byte[] bytes;
        try (InputStream classStream = Objects.requireNonNull(ASMHelper.class.getClassLoader().getResourceAsStream(classObject.getName().replace('.', '/').concat(".class")))) {
            bytes = new byte[classStream.available()];
            if (classStream.read(bytes) == -1)
                throw new RuntimeException("Couldn't read class \"" + classObject.getName() + "\"!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bytes;
	}
	
	public static String toTarget(MethodNode method) {
		return method.name + method.desc;
	}

    public static String toTarget(Class<?> owner, MethodNode methodNode) {
        return Type.getDescriptor(owner) + methodNode.name + methodNode.desc;
    }

    public static String toTarget(ClassNode classNode, MethodNode methodNode) {
        return "L" + classNode.name + ";" + methodNode.name + methodNode.desc;
    }

    public static String toTarget(MethodInsnNode methodInsn) {
        return "L" + methodInsn.owner + ";" + methodInsn.name + methodInsn.desc;
    }
}
