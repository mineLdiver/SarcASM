package net.mine_diver.sarcasm.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class Reflection {
    public static Field publicField(Field field) {
        try {
            field.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (Exception e) {
            if (field == null)
                throw new RuntimeException("Field is null", e);
            else
                throw new RuntimeException("Field name: " + field.getName(), e);
        }
        return field;
    }

    private static final Field modifiersField;
    static {
        try {
            modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
