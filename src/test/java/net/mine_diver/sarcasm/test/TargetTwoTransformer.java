package net.mine_diver.sarcasm.test;

import net.mine_diver.sarcasm.transformer.ProxyTransformer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;

import java.util.stream.StreamSupport;

import static org.objectweb.asm.Opcodes.LDC;

public class TargetTwoTransformer implements ProxyTransformer {
    @Override
    public String[] getRequestedMethods() {
        return new String[] {
                "testRecursion()Ljava/lang/String;"
        };
    }

    @Override
    public void transform(ClassNode node) {
        node.methods.stream().filter(methodNode -> "testRecursion".equals(methodNode.name)).findFirst().flatMap(methodNode -> StreamSupport.stream(methodNode.instructions.spliterator(), false).filter(node1 -> LDC == node1.getOpcode()).map(node1 -> (LdcInsnNode) node1).findFirst()).ifPresent(ldcInsnNode -> ldcInsnNode.cst = "Recursion works!");
    }
}
