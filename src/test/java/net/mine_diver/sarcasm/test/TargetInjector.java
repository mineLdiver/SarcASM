package net.mine_diver.sarcasm.test;

import net.mine_diver.sarcasm.injector.ProxyInjector;
import net.mine_diver.sarcasm.util.Reflection;

public class TargetInjector implements ProxyInjector<Target> {

    @Override
    public Target getTargetInstance() {
        return Target.INSTANCE;
    }

    @Override
    public void inject(Target proxyInstance) {
        try {
            Reflection.publicField(Target.class.getDeclaredField("INSTANCE")).set(null, proxyInstance);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
