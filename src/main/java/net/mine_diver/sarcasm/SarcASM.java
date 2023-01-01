package net.mine_diver.sarcasm;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

import net.mine_diver.sarcasm.injector.ProxyInjector;
import net.mine_diver.sarcasm.transformer.ProxyTransformer;
import net.mine_diver.sarcasm.util.ASMHelper;
import net.mine_diver.sarcasm.util.Reflection;
import net.mine_diver.sarcasm.util.Util;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * Severely Abhorrent and Ridiculously Convoluted ASM
 * 
 * <p>This simple system allows dynamic generation and injection of proxy classes.
 * 
 * @author mine_diver
 */
public final class SarcASM {
	private SarcASM() {}

	private static final Logger LOGGER = Logger.getLogger("SarcASM");
	static {
		LOGGER.setUseParentHandlers(false);
		ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new SimpleFormatter() {
			private static final String format = "[%1$tF] [%1$tT] [%2$s] [%3$s#%4$s] [%5$s]: %6$s %n";

			@Override
			public synchronized String format(LogRecord lr) {
				String className = lr.getSourceClassName();
				return String.format(format,
						new Date(lr.getMillis()),
						lr.getLoggerName(),
						className.substring(className.lastIndexOf(".") + 1),
						lr.getSourceMethodName(),
						lr.getLevel().getLocalizedName(),
						lr.getMessage()
				);
			}
		});
		LOGGER.addHandler(handler);
	}
	private static final Map<Class<?>, Set<ProxyInjector<?>>> INJECTORS = new IdentityHashMap<>();
	private static final Map<Class<?>, Set<ProxyTransformer>> TRANSFORMERS = new IdentityHashMap<>();

	
	public static <T> void registerInjector(Class<T> targetClass, ProxyInjector<T> injector) {
		if (!INJECTORS.computeIfAbsent(Objects.requireNonNull(targetClass), value -> Util.newIdentitySet()).add(Objects.requireNonNull(injector)))
			LOGGER.warning("Tried registering the same \"" + targetClass.getName() + "\" injector at \"" + injector.getClass().getName() + "\" twice. Please check your code");
		if (TRANSFORMERS.containsKey(targetClass))
			initProxyFor(targetClass);
			
	}
	
	public static <T> void registerTransformer(Class<T> targetClass, ProxyTransformer transformer) {
		if (!TRANSFORMERS.computeIfAbsent(Objects.requireNonNull(targetClass), value -> Util.newIdentitySet()).add(Objects.requireNonNull(transformer)))
			LOGGER.warning("Tried registering the same \"" + targetClass.getName() + "\" transformer at \"" + transformer.getClass().getName() + "\" twice. Please check your code");
		if (INJECTORS.containsKey(targetClass))
			initProxyFor(targetClass);
	}
	
	@SuppressWarnings("unchecked")
	public static <T, P extends T> void initProxyFor(Class<T> targetClass) {
		
		// sanity checks
		Set<ProxyInjector<T>> injectors = (Set<ProxyInjector<T>>) (Set<?>) INJECTORS.get(targetClass);
		if (injectors == null)
			throw new IllegalArgumentException("There are no registered injectors for class \"" + targetClass.getName() + "\"! Terminating");
		Set<ProxyTransformer> transformers = TRANSFORMERS.get(targetClass);
		if (transformers == null) {
			LOGGER.info("\"" + targetClass.getName() + "\" has no transformers. Skipping");
			return;
		}
		
		// preparations
		Set<String> requestedMethods = transformers.stream().flatMap(transformer -> Arrays.stream(transformer.getRequestedMethods())).collect(Collectors.toSet());
		ClassReader targetReader = new ClassReader(ASMHelper.readClassBytes(targetClass));
		ClassNode targetNode = new ClassNode();
		targetReader.accept(targetNode, 0);
		
		// proxy class generation
		ClassWriter proxyGenerator = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		proxyGenerator.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, Type.getInternalName(targetClass), null, Type.getInternalName(targetClass), null);
		proxyGenerator.visitEnd();
		ClassNode proxyNode = new ClassNode();
		new ClassReader(proxyGenerator.toByteArray()).accept(proxyNode, 0);
		
		// initializing requested methods
		targetNode.methods.forEach(targetMethod -> {
			if (requestedMethods.remove(ASMHelper.toTarget(targetMethod)))
				proxyNode.methods.add(targetMethod);
		});
		if (!requestedMethods.isEmpty())
			throw new IllegalArgumentException("Couldn't find some requested methods for class \"" + targetClass.getName() + "\", such as: " + requestedMethods);
		
		// transforming
		transformers.forEach(transformer -> transformer.transform(proxyNode));
		
		// defining
		ClassWriter proxyWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		proxyNode.accept(proxyWriter);
		Class<P> proxyClass = (Class<P>) Util.UNSAFE.defineAnonymousClass(targetClass, proxyWriter.toByteArray(), null);
		
		// injecting
		injectors.stream().collect(Collectors.groupingBy(ProxyInjector::getTargetInstance, IdentityHashMap::new, Collectors.toCollection(Util::newIdentitySet))).forEach((targetInstance, targetInjectors) -> {
			P proxyInstance;
			try {
				proxyInstance = (P) Util.UNSAFE.allocateInstance(proxyClass);
			} catch (InstantiationException e) {
				throw new RuntimeException(e);
			}
			for (Field field : targetClass.getDeclaredFields())
				if (!Modifier.isStatic(field.getModifiers())) {
					try {
						Reflection.publicField(field).set(proxyInstance, Reflection.publicField(field).get(targetInstance));
					} catch (IllegalArgumentException | IllegalAccessException e) {
						throw new RuntimeException(e);
					}
				}
			targetInjectors.forEach(targetInjector -> targetInjector.inject(proxyInstance));
		});
	}
}
