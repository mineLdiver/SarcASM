package net.mine_diver.sarcasm;

import net.mine_diver.sarcasm.injector.ProxyInjector;
import net.mine_diver.sarcasm.transformer.ProxyTransformer;
import net.mine_diver.sarcasm.transformer.RequestedMethodsTransformer;
import net.mine_diver.sarcasm.transformer.SuperSuperTransformer;
import net.mine_diver.sarcasm.util.ASMHelper;
import net.mine_diver.sarcasm.util.Reflection;
import net.mine_diver.sarcasm.util.Util;
import net.mine_diver.sarcasm.util.collection.ConditionalSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.V1_8;

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
	private static final Map<Class<?>, ConditionalSet<ProxyTransformer>> TRANSFORMERS = new IdentityHashMap<>();


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
		RequestedMethodsTransformer<T> rm = RequestedMethodsTransformer.of(targetClass);
		SuperSuperTransformer<T> ss = SuperSuperTransformer.of(targetClass);
		ConditionalSet<ProxyTransformer> transformers = TRANSFORMERS.computeIfAbsent(Objects.requireNonNull(targetClass), value -> {
			ConditionalSet<ProxyTransformer> set = new ConditionalSet<>();
			set.entry(rm).before(ss).add();
			set.entry(ss).after(rm).add();
			return set;
		});
		transformers.entry(Objects.requireNonNull(transformer)).after(rm).before(ss).add();
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
	 * @param factory the target factory. Must always return a new instance of the target
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

	/**
	 * If available, returns a stream of all transformers for the target class in the correct order.
	 *
	 * @param targetClass the target class
	 * @return an optional of a stream of all transformers for the target class in the correct order
	 * @param <T> the target type
	 */
	public static <T> Optional<Stream<ProxyTransformer>> streamTransformers(final Class<T> targetClass) {
		return TRANSFORMERS.containsKey(targetClass) ? Optional.of(TRANSFORMERS.get(targetClass).stream()) : Optional.empty();
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
		final ConditionalSet<ProxyTransformer> transformers = TRANSFORMERS.get(targetClass);
		if (transformers == null) {
			LOGGER.info("\"" + targetClass.getName() + "\" has no transformers. Skipping");
			return null;
		}

		// preparations
		final ClassReader targetReader = new ClassReader(ASMHelper.readClassBytes(targetClass));
		final ClassNode targetNode = new ClassNode();
		targetReader.accept(targetNode, ClassReader.EXPAND_FRAMES);

		// proxy class generation
		final ClassNode proxyNode = new ClassNode();
		proxyNode.visit(V1_8, ACC_PUBLIC, Type.getInternalName(targetClass), null, Type.getInternalName(targetClass), null);
		proxyNode.visitEnd();

		// transforming
		transformers.forEach(transformer -> transformer.transform(proxyNode));

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
