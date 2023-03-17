package net.mine_diver.sarcasm;

import net.mine_diver.sarcasm.injector.ProxyInjector;
import net.mine_diver.sarcasm.transformer.ProxyTransformer;
import net.mine_diver.sarcasm.util.ASMHelper;
import net.mine_diver.sarcasm.util.Reflection;
import net.mine_diver.sarcasm.util.Util;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.objectweb.asm.Opcodes.*;

/**
 * Severely Abhorrent and Ridiculously Convoluted ASM
 * 
 * <p>This simple system allows dynamic generation and injection of proxy classes.
 * This can be very helpful when two or more projects try to replace the same instance
 * of a class and end up overwriting each other.
 *
 * <p>SarcASM gathers all methods requested by transformers, pastes them as-is into
 * the generated proxy classes, passes the generated ClassNode to transformers,
 * defines the now transformed proxy class as an anonymous class of the target, giving
 * direct access to private members, then invokes injectors for the target class
 * and creates a full copy of the instance an injector is capable of replacing,
 * allowing for seamless injection of the proxy class.
 *
 * @author mine_diver
 */
public final class SarcASM {
	private SarcASM() {}

	private static final Logger LOGGER = Logger.getLogger("SarcASM");
	static {
		LOGGER.setUseParentHandlers(false);
		final ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new SimpleFormatter() {
			private static final String format = "[%1$tF] [%1$tT] [%2$s] [%3$s#%4$s] [%5$s]: %6$s %n";

			@Override
			public synchronized String format(LogRecord lr) {
				final String className = lr.getSourceClassName();
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
	private static final boolean DEBUG_EXPORT = Boolean.getBoolean("sarcasm.debug.export");
	private static final int MODIFIER_OFFSET = 152;
	private static final int ACCESS_FLAG_OFFSET = 156;
	private static final Map<Class<?>, Class<?>> PROXY_CLASSES = new IdentityHashMap<>();
	private static final Map<Class<?>, Set<ProxyInjector<?>>> INJECTORS = new IdentityHashMap<>();
	private static final Map<Class<?>, Set<ProxyTransformer>> TRANSFORMERS = new IdentityHashMap<>();


	/**
	 * Registers an injector for the given class and initializes proxy if transformers already exist.
	 *
	 * <p>Injectors for the same instance do not conflict unless coded poorly.
	 *
	 * @param targetClass the target class
	 * @param injector the injector instance
	 * @param <T> the target class type
	 */
	public static <T> void registerInjector(final Class<T> targetClass, final ProxyInjector<T> injector) {
		if (!INJECTORS.computeIfAbsent(Objects.requireNonNull(targetClass), value -> Util.newIdentitySet()).add(Objects.requireNonNull(injector)))
			LOGGER.warning("Tried registering the same \"" + targetClass.getName() + "\" injector at \"" + injector.getClass().getName() + "\" twice. Please check your code");
		if (TRANSFORMERS.containsKey(targetClass))
			initProxyFor(targetClass);
	}

	/**
	 * Registers a transformer for the given class and initializes proxy if injectors already exist.
	 *
	 * <p>Transformers of the same method do not conflict unless coded poorly.
	 *
	 * @param targetClass the target class
	 * @param transformer the transformer instance
	 * @param <T> the target class type
	 */
	public static <T> void registerTransformer(final Class<T> targetClass, final ProxyTransformer transformer) {
		if (!TRANSFORMERS.computeIfAbsent(Objects.requireNonNull(targetClass), value -> Util.newIdentitySet()).add(Objects.requireNonNull(transformer)))
			LOGGER.warning("Tried registering the same \"" + targetClass.getName() + "\" transformer at \"" + transformer.getClass().getName() + "\" twice. Please check your code");
		PROXY_CLASSES.remove(targetClass);
		if (INJECTORS.containsKey(targetClass))
			initProxyFor(targetClass);
	}

	/**
	 * Injects the proxy class for the given target class.
	 *
	 * <p>Since a proxy is already initialized once both injectors and transformers are registered,
	 * this should only be called if the proxy instance got invalidated by some 3rd-party code.
	 *
	 * @param targetClass the target class
	 * @param <T> the target class type
	 * @param <P> the proxy class type
	 */
	public static <T, P extends T> void initProxyFor(final Class<T> targetClass) {
		// sanity checks
		//noinspection unchecked
		final Set<ProxyInjector<T>> injectors = (Set<ProxyInjector<T>>) (Set<?>) INJECTORS.get(targetClass);
		if (injectors == null) {
			LOGGER.info("\"" + targetClass.getName() + "\" has no injectors. Skipping");
			return;
		}

		getProxyClass(targetClass).ifPresent(proxyClass -> {
			// injecting
			injectors.stream().filter(tProxyInjector -> tProxyInjector.getTargetInstance() != null).collect(Collectors.groupingBy(ProxyInjector::getTargetInstance, IdentityHashMap::new, Collectors.toCollection(Util::newIdentitySet))).forEach((target, targetInjectors) -> {
				final P proxyInstance = createShallowProxy(targetClass, proxyClass, target);
				targetInjectors.forEach(targetInjector -> targetInjector.inject(proxyInstance));
			});
		});
	}

	/**
	 * Creates a new target instance using the provided factory and either
	 * wraps it with the proxy class, or returns the new target instance itself
	 * if there are no transformers registered for this class yet.
	 *
	 * <p>
	 *     Should only be used to create short-living instances of the target class,
	 *     as there's no way to automatically renew them in a case that a new transformer is registered.
	 * </p>
	 *
	 * @param factory the target factory. Must always return a new instance of the target.
	 * @return either a new instance of target wrapped with the proxy class, or the new target instance itself in the case
	 * that there are no transformers registered for this class
	 * @param <T> the target type
	 */
	public static <T> T newUntrackedProxy(final Supplier<T> factory) {
		final T target = factory.get();
		//noinspection unchecked
		final Class<T> targetClass = (Class<T>) target.getClass();
		return getProxyClass(targetClass).map(proxyClass -> (T) createShallowProxy(targetClass, proxyClass, target)).orElse(target);
	}

	private static <T, P extends T> P createShallowProxy(final Class<T> targetClass, final Class<T> proxyClass, final T target) {
		final P proxyInstance;
		try {
			//noinspection unchecked
			proxyInstance = (P) Util.UNSAFE.allocateInstance(proxyClass);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		}
		Class<?> currentClass = targetClass;
		while (currentClass != null) {
			for (Field field : currentClass.getDeclaredFields())
				if (!Modifier.isStatic(field.getModifiers())) try {
					Reflection.publicField(field).set(proxyInstance, Reflection.publicField(field).get(target));
				} catch (final IllegalArgumentException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			currentClass = currentClass.getSuperclass();
		}
		return proxyInstance;
	}

	private static <T, P extends T> Optional<Class<P>> getProxyClass(final Class<T> targetClass) {
		//noinspection unchecked
		return Optional.ofNullable((Class<P>) PROXY_CLASSES.computeIfAbsent(targetClass, SarcASM::generateProxyClass));
	}

	private static <T, P extends T> Class<P> generateProxyClass(final Class<T> targetClass) {
		// sanity checks
		final Set<ProxyTransformer> transformers = TRANSFORMERS.get(targetClass);
		if (transformers == null) {
			LOGGER.info("\"" + targetClass.getName() + "\" has no transformers. Skipping");
			return null;
		}

		// preparations
		final Set<String> requestedMethods = transformers.stream().flatMap(transformer -> Arrays.stream(transformer.getRequestedMethods())).collect(Collectors.toSet());
		final ClassReader targetReader = new ClassReader(ASMHelper.readClassBytes(targetClass));
		final ClassNode targetNode = new ClassNode();
		targetReader.accept(targetNode, ClassReader.EXPAND_FRAMES);

		// proxy class generation
		final ClassNode proxyNode = new ClassNode();
		proxyNode.visit(V1_8, ACC_PUBLIC, Type.getInternalName(targetClass), null, Type.getInternalName(targetClass), null);
		proxyNode.visitEnd();

		// initializing requested methods
		targetNode.methods.stream().filter(targetMethod -> requestedMethods.remove(ASMHelper.toTarget(targetMethod))).forEach(methodNode -> {
			final MethodNode proxyMethod = new MethodNode();
			methodNode.accept(proxyMethod);
			methodNode.localVariables.get(0).desc = Type.getObjectType(proxyNode.name).getDescriptor();
			proxyNode.methods.add(methodNode);
		});
		if (!requestedMethods.isEmpty())
			throw new IllegalArgumentException("Couldn't find some requested methods for class \"" + targetClass.getName() + "\", such as: " + requestedMethods);

		// transforming
		transformers.forEach(transformer -> transformer.transform(proxyNode));

		// super.super implementation
		final Map<String, FieldNode> methodHandles = new HashMap<>();
		new ArrayList<>(proxyNode.methods).forEach(methodNode -> {
			StreamSupport.stream(methodNode.instructions.spliterator(), false).filter(abstractInsnNode -> abstractInsnNode.getType() == AbstractInsnNode.METHOD_INSN).map(abstractInsnNode -> ((MethodInsnNode) abstractInsnNode)).filter(methodInsnNode -> methodInsnNode.owner.equals(targetNode.superName) && targetNode.methods.stream().anyMatch(methodNode1 -> methodInsnNode.name.equals(methodNode1.name) && methodInsnNode.desc.equals(methodNode1.desc))).forEach(methodInsnNode -> {
				final String target = ASMHelper.toTarget(methodInsnNode);
				if (!methodHandles.containsKey(target)) {
					final FieldNode methodHandle = new FieldNode(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "SARCASM$super_" + methodInsnNode.name, Type.getDescriptor(MethodHandle.class), null, null);
					methodHandles.put(target, methodHandle);
					proxyNode.fields.add(methodHandle);
					final InsnList mhInit = new InsnList();
					mhInit.add(new FieldInsnNode(GETSTATIC, Type.getInternalName(Util.class), "IMPL_LOOKUP", Type.getDescriptor(MethodHandles.Lookup.class)));
					mhInit.add(new LdcInsnNode(Type.getObjectType(targetNode.superName)));
					mhInit.add(new LdcInsnNode(methodInsnNode.name));
					mhInit.add(new LdcInsnNode(Type.getMethodType(methodInsnNode.desc)));
					mhInit.add(new LdcInsnNode(Type.getObjectType(targetNode.superName)));
					mhInit.add(new MethodInsnNode(INVOKEVIRTUAL, Type.getInternalName(MethodHandles.Lookup.class), "findSpecial", Type.getMethodDescriptor(Type.getType(MethodHandle.class), Type.getType(Class.class), Type.getType(String.class), Type.getType(MethodType.class), Type.getType(Class.class))));
					mhInit.add(new FieldInsnNode(PUTSTATIC, proxyNode.name, methodHandle.name, methodHandle.desc));
					final InsnList clinitInsns = proxyNode.methods.stream().filter(methodNode1 -> methodNode1.name.equals("<clinit>")).findFirst().orElseGet(() -> {
						final MethodNode clinit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
						clinit.instructions.add(new InsnNode(RETURN));
						proxyNode.methods.add(clinit);
						return clinit;
					}).instructions;
					clinitInsns.insertBefore(clinitInsns.getLast(), mhInit);
				}
				final FieldNode methodHandle = methodHandles.get(target);
				final InsnList superCall = new InsnList();
				final LabelNode startTry = new LabelNode();
				superCall.add(startTry);
				final Type[] argumentTypes = Util.concat(Type.getObjectType(proxyNode.name), Type.getArgumentTypes(methodInsnNode.desc));
				int curArg = methodNode.maxLocals;
				for (final Type argumentType : argumentTypes)
					ASMHelper.addLocalVariable(methodNode, argumentType.getDescriptor());
				for (int i = argumentTypes.length - 1; i >= 0; i--)
					superCall.add(new VarInsnNode(argumentTypes[i].getOpcode(ISTORE), methodNode.maxLocals - argumentTypes.length + i));
				superCall.add(new FieldInsnNode(GETSTATIC, proxyNode.name, methodHandle.name, methodHandle.desc));
				for (Type argumentType : argumentTypes)
					superCall.add(new VarInsnNode(argumentType.getOpcode(ILOAD), curArg++));
				superCall.add(new MethodInsnNode(INVOKEVIRTUAL, Type.getInternalName(MethodHandle.class), "invoke", Type.getMethodDescriptor(Type.getReturnType(methodInsnNode.desc), argumentTypes)));
				final LabelNode endTry = new LabelNode();
				superCall.add(endTry);
				final LabelNode exitTry = new LabelNode();
				superCall.add(new JumpInsnNode(GOTO, exitTry));
				final LabelNode handleTry = new LabelNode();
				superCall.add(handleTry);
				final LocalVariableNode throwable = ASMHelper.addLocalVariable(methodNode, ASMHelper.allocateLocal(methodNode), "e", Type.getDescriptor(Throwable.class), handleTry, exitTry);
				superCall.add(new VarInsnNode(ASTORE, throwable.index));
				superCall.add(new TypeInsnNode(NEW, Type.getInternalName(RuntimeException.class)));
				superCall.add(new InsnNode(DUP));
				superCall.add(new VarInsnNode(ALOAD, throwable.index));
				superCall.add(new MethodInsnNode(INVOKESPECIAL, Type.getInternalName(RuntimeException.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Throwable.class))));
				superCall.add(new InsnNode(ATHROW));
				superCall.add(exitTry);
				methodNode.instructions.insertBefore(methodInsnNode, superCall);
				methodNode.instructions.remove(methodInsnNode);
				methodNode.tryCatchBlocks.add(new TryCatchBlockNode(startTry, endTry, handleTry, Type.getInternalName(Throwable.class)));
			});
			methodNode.localVariables.get(0).start = ASMHelper.getStartLabel(methodNode);
		});
		if (!methodHandles.isEmpty())
			proxyNode.innerClasses.add(new InnerClassNode("java/lang/invoke/MethodHandles$Lookup", "java/lang/invoke/MethodHandles", "Lookup", ACC_PUBLIC | ACC_STATIC | ACC_FINAL));

		// making sure the target class is inheritable
		// (proxies can't override methods in final classes though)
		final T dummyInstance;
		try {
			//noinspection unchecked
			dummyInstance = (T) Util.UNSAFE.allocateInstance(targetClass);
		} catch (final InstantiationException e) {
			throw new RuntimeException(e);
		}
		final long klassPointer = Util.UNSAFE.arrayIndexScale(Object[].class) == 4 ? (Util.UNSAFE.getInt(dummyInstance, 8L) & 0xFFFFFFFFL) << 3 : Util.UNSAFE.getLong(dummyInstance, 8L);
		Util.UNSAFE.putInt(klassPointer + MODIFIER_OFFSET, Util.UNSAFE.getInt(klassPointer + MODIFIER_OFFSET) & ~Modifier.FINAL);
		Util.UNSAFE.putInt(klassPointer + ACCESS_FLAG_OFFSET, Util.UNSAFE.getInt(klassPointer + ACCESS_FLAG_OFFSET) & ~Modifier.FINAL);

		// defining
		final ClassWriter proxyWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		proxyNode.accept(proxyWriter);
		final byte[] proxyBytes = proxyWriter.toByteArray();
		debugExport(proxyNode, proxyBytes);
		//noinspection unchecked
		return (Class<P>) Util.UNSAFE.defineAnonymousClass(targetClass, proxyBytes, null).asSubclass(targetClass);
	}

	private static void debugExport(final ClassNode proxyNode, final byte[] proxyBytes) {
		if (DEBUG_EXPORT) {
			final File exportLoc = new File(".sarcasm.out/class/" + proxyNode.name + ".class");
			//noinspection ResultOfMethodCallIgnored
			exportLoc.getParentFile().mkdirs();
			final FileOutputStream file;
			try {
				file = new FileOutputStream(exportLoc);
			} catch (final FileNotFoundException e) {
				throw new RuntimeException(e);
			}
			try {
				file.write(proxyBytes);
				file.close();
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
