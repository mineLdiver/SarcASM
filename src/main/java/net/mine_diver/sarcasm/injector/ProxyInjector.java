package net.mine_diver.sarcasm.injector;

public interface ProxyInjector<T> {
    /**
     * An injector has to work for a specific instance of the target class,
     * since there can be multiple and in multiple places.
     *
     * <p>For example, Target.INSTANCE and Main.targetInstance might be different
     * instances with different field values, thus requiring a different
     * proxy instance for each of them.
     *
     * <p>DO NOT CACHE THIS, this method has to provide direct access to
     * the field/array/etc the injector is going to inject into.
     * Even if there's already a proxy in that point, it won't cause
     * side effects, because target's class is already set and field values
     * can simply be taken from another proxy.
     *
     * @return the target instance this injector is working with
     */
    T getTargetInstance();

    /**
     * The proxy instance injection code must not conflict with identical
     * injectors. SarcASM specifically groups all injectors by their target
     * instances so in the worst case scenario they'll just inject the same
     * instance multiple times, causing no side effects other than slower
     * injection time.
     *
     * @param proxyInstance the proxy instance to inject
     */
    void inject(T proxyInstance);
}
