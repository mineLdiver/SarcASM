/*
 * This file is part of Mixin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package net.mine_diver.sarcasm.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public final class Bytecode {
    private Bytecode() {}

    /**
     * Gets a description of the supplied node for debugging purposes
     *
     * @param node node to describe
     * @param listFormat format the returned string so that returned nodes line
     *      up (node names aligned to 14 chars)
     * @return human-readable description of node
     */
    public static String describeNode(AbstractInsnNode node, boolean listFormat) {
        if (node == null) {
            return listFormat ? String.format("   %-14s ", "null") : "null";
        }

        if (node instanceof LabelNode) {
            return String.format("[%s]", ((LabelNode)node).getLabel());
        }

        String out = String.format(listFormat ? "   %-14s " : "%s ", node.getClass().getSimpleName().replace("Node", ""));
        if (node instanceof JumpInsnNode) {
            out += String.format("[%s] [%s]", Bytecode.getOpcodeName(node), ((JumpInsnNode)node).label.getLabel());
        } else if (node instanceof VarInsnNode) {
            out += String.format("[%s] %d", Bytecode.getOpcodeName(node), ((VarInsnNode)node).var);
        } else if (node instanceof MethodInsnNode) {
            MethodInsnNode mth = (MethodInsnNode)node;
            out += String.format("[%s] %s::%s%s", Bytecode.getOpcodeName(node), mth.owner, mth.name, mth.desc);
        } else if (node instanceof FieldInsnNode) {
            FieldInsnNode fld = (FieldInsnNode)node;
            out += String.format("[%s] %s::%s:%s", Bytecode.getOpcodeName(node), fld.owner, fld.name, fld.desc);
        } else if (node instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode idc = (InvokeDynamicInsnNode)node;
            out += String.format("[%s] %s%s { %s %s::%s%s }", Bytecode.getOpcodeName(node), idc.name, idc.desc,
                    Bytecode.getOpcodeName(idc.bsm.getTag(), "H_GETFIELD", 1), idc.bsm.getOwner(), idc.bsm.getName(), idc.bsm.getDesc());
        } else if (node instanceof LineNumberNode) {
            LineNumberNode ln = (LineNumberNode)node;
            out += String.format("LINE=[%d] LABEL=[%s]", ln.line, ln.start.getLabel());
        } else if (node instanceof LdcInsnNode) {
            out += (((LdcInsnNode)node).cst);
        } else if (node instanceof IntInsnNode) {
            out += (((IntInsnNode)node).operand);
        } else if (node instanceof FrameNode) {
            out += String.format("[%s] ", Bytecode.getOpcodeName(((FrameNode)node).type, "H_INVOKEINTERFACE", -1));
        } else if (node instanceof TypeInsnNode) {
            out += String.format("[%s] %s", Bytecode.getOpcodeName(node), ((TypeInsnNode)node).desc);
        } else {
            out += String.format("[%s] ", Bytecode.getOpcodeName(node));
        }
        return out;
    }

    /**
     * Uses reflection to find an approximate constant name match for the
     * supplied node's opcode
     *
     * @param node Node to query for opcode
     * @return Approximate opcode name (approximate because some constants in
     *      the {@link Opcodes} class have the same value as opcodes
     */
    public static String getOpcodeName(AbstractInsnNode node) {
        return node != null ? Bytecode.getOpcodeName(node.getOpcode()) : "";
    }

    /**
     * Uses reflection to find an approximate constant name match for the
     * supplied opcode
     *
     * @param opcode Opcode to look up
     * @return Approximate opcode name (approximate because some constants in
     *      the {@link Opcodes} class have the same value as opcodes
     */
    public static String getOpcodeName(int opcode) {
        return Bytecode.getOpcodeName(opcode, "UNINITIALIZED_THIS", 1);
    }

    private static String getOpcodeName(int opcode, String start, int min) {
        if (opcode >= min) {
            boolean found = false;

            try {
                for (java.lang.reflect.Field f : Opcodes.class.getDeclaredFields()) {
                    if (!found && !f.getName().equals(start)) {
                        continue;
                    }
                    found = true;
                    if (f.getType() == Integer.TYPE && f.getInt(null) == opcode) {
                        return f.getName();
                    }
                }
            } catch (Exception ex) {
                // derp
            }
        }

        return opcode >= 0 ? String.valueOf(opcode) : "UNKNOWN";
    }

    /**
     * Returns true if the supplied method node is static
     *
     * @param method method node
     * @return true if the method has the {@link Opcodes#ACC_STATIC} flag
     */
    public static boolean isStatic(MethodNode method) {
        return (method.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
    }

    /**
     * Get the first variable index in the supplied method which is not an
     * argument or "this" reference, this corresponds to the size of the
     * arguments passed in to the method plus an extra spot for "this" if the
     * method is non-static
     *
     * @param method MethodNode to inspect
     * @return first available local index which is NOT used by a method
     *      argument or "this"
     */
    public static int getFirstNonArgLocalIndex(MethodNode method) {
        return Bytecode.getFirstNonArgLocalIndex(Type.getArgumentTypes(method.desc), !Bytecode.isStatic(method));
    }

    /**
     * Get the first non-arg variable index based on the supplied arg array and
     * whether to include the "this" reference, this corresponds to the size of
     * the arguments passed in to the method plus an extra spot for "this" is
     * specified
     *
     * @param args Method arguments
     * @param includeThis Whether to include a slot for "this" (generally true
     *      for all non-static methods)
     * @return first available local index which is NOT used by a method
     *      argument or "this"
     */
    public static int getFirstNonArgLocalIndex(Type[] args, boolean includeThis) {
        return Bytecode.getArgsSize(args) + (includeThis ? 1 : 0);
    }

    /**
     * Get the size of the specified args array in local variable terms (eg.
     * doubles and longs take two spaces)
     *
     * @param args Method argument types as array
     * @return size of the specified arguments array in terms of stack slots
     */
    public static int getArgsSize(Type[] args) {
        return Bytecode.getArgsSize(args, 0, args.length);
    }

    /**
     * Get the size of the specified args array in local variable terms (eg.
     * doubles and longs take two spaces) using startIndex (inclusive) and
     * endIndex (exclusive) to determine which arguments to process.
     *
     * @param args Method argument types as array
     * @param startIndex Start index in the array, not related to arg size
     * @param endIndex End index (exclusive) in the array, not related to size
     * @return size of the specified arguments array in terms of stack slots
     */
    public static int getArgsSize(Type[] args, int startIndex, int endIndex) {
        int size = 0;

        for (int index = startIndex; index < args.length && index < endIndex; index++) {
            size += args[index].getSize();
        }

        return size;
    }
}
