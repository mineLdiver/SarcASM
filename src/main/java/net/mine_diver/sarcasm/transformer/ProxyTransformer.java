package net.mine_diver.sarcasm.transformer;

import org.objectweb.asm.tree.ClassNode;

public interface ProxyTransformer {
	
	String[] getRequestedMethods();
	
	void transform(ClassNode node);
}
