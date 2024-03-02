package net.mine_diver.sarcasm.test;

import net.mine_diver.sarcasm.transformer.ProxyTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.PrintStream;

public class TargetTransformer implements ProxyTransformer {

    @Override
    public String[] getRequestedMethods() {
        return new String[] {
                "test(Ljava/lang/String;)V"
        };
    }

    @Override
    public void transform(ClassNode node) {
        InsnList targetInsns = node.methods.stream().filter(methodNode -> "test".equals(methodNode.name)).findFirst().orElseThrow(IllegalStateException::new).instructions;
        InsnList injectInsns = new InsnList();
        injectInsns.add(new FieldInsnNode(Opcodes.GETSTATIC, Type.getInternalName(System.class), "out", "L" + Type.getInternalName(PrintStream.class) + ";"));
        injectInsns.add(new LdcInsnNode("Injected print!"));
        injectInsns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class))));
        targetInsns.insert(injectInsns);
    }
}
