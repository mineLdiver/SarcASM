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

import org.objectweb.asm.tree.FrameNode;

/**
 * Information about frames in a method
 */
public class FrameData {
    private static final String[] FRAMETYPES = { "NEW", "FULL", "APPEND", "CHOP", "SAME", "SAME1" };

    /**
     * Frame index
     */
    public final int index;

    /**
     * Frame type
     */
    public final int type;

    /**
     * Frame local count
     */
    public final int locals;

    /**
     * Frame local size
     */
    public final int size;
    public final int rawSize; // Fabric non-adjusted frame size for legacy support

    FrameData(int index, int type, int locals, int size) {
        this.index = index;
        this.type = type;
        this.locals = locals;
        this.size = size;
        this.rawSize = size;
    }

    FrameData(int index, FrameNode frameNode, int initialFrameSize) {
        this.index = index;
        this.type = frameNode.type;
        this.locals = frameNode.local != null ? frameNode.local.size() : 0;
        this.rawSize = Locals.computeFrameSize(frameNode, 0);
        this.size = Math.max(rawSize, initialFrameSize);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("FrameData[index=%d, type=%s, locals=%d size=%d]", this.index, FrameData.FRAMETYPES[this.type + 1], this.locals,
                this.size);
    }
}