package net.mine_diver.sarcasm.util.function;

public interface CheckedFunction<T, R, E extends Throwable> {
    R apply(T t) throws E;
}
