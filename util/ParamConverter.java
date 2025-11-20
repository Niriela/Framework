package framework.util;

public final class ParamConverter {

    private ParamConverter() {}

    public static Object convert(String value, Class<?> targetType) {
        if (value == null || value.isEmpty()) {
            return targetType.isPrimitive() ? defaultPrimitiveValue(targetType) : null;
        }
        try {
            if (targetType == String.class) return value;
            if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value);
            if (targetType == long.class || targetType == Long.class) return Long.parseLong(value);
            if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
            if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value);
            if (targetType == float.class || targetType == Float.class) return Float.parseFloat(value);
            if (targetType == short.class || targetType == Short.class) return Short.parseShort(value);
            if (targetType == byte.class || targetType == Byte.class) return Byte.parseByte(value);
            if (targetType == char.class || targetType == Character.class) return value.charAt(0);
        } catch (Exception ex) {
            // parsing failed -> retourne valeur par défaut pour primitifs, null sinon
            return targetType.isPrimitive() ? defaultPrimitiveValue(targetType) : null;
        }
        // type non géré
        return null;
    }

    public static Object defaultPrimitiveValue(Class<?> primitiveType) {
        if (primitiveType == int.class) return 0;
        if (primitiveType == long.class) return 0L;
        if (primitiveType == boolean.class) return false;
        if (primitiveType == double.class) return 0.0d;
        if (primitiveType == float.class) return 0.0f;
        if (primitiveType == short.class) return (short) 0;
        if (primitiveType == byte.class) return (byte) 0;
        if (primitiveType == char.class) return '\0';
        return null;
    }
}