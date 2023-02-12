package net.mine_diver.sarcasm.util;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class Util {

    public static final Unsafe UNSAFE;
    public static final MethodHandles.Lookup IMPL_LOOKUP;
    static {
        try {
            Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafeField.get(null);
            Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            IMPL_LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(UNSAFE.staticFieldBase(implLookupField), UNSAFE.staticFieldOffset(implLookupField));
        } catch (IllegalArgumentException | SecurityException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }

    public static <T> Set<T> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    public static <T> T[] concat(T element, T[] array) {
        //noinspection unchecked
        Class<T[]> arrayType = (Class<T[]>) array.getClass();
        //noinspection unchecked
        T[] newArray = (arrayType == (Object)Object[].class) ?
                (T[]) new Object[array.length + 1] :
                (T[]) Array.newInstance(arrayType.getComponentType(), array.length + 1);
        System.arraycopy(array, 0, newArray, 1, array.length);
        newArray[0] = element;
        return newArray;
    }
}
