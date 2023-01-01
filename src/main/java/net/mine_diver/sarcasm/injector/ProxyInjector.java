package net.mine_diver.sarcasm.injector;

public interface ProxyInjector<T> {
	
	T getTargetInstance();
	
	void inject(T proxyInstance);
}
