package framework.util;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Représente le mapping d'une classe : classe + ses méthodes mappées par URL.
 */
public class UrlMapping {
    private final Class<?> mappedClass;
    private final Map<String, Method> urlToMethod;

    public UrlMapping(Class<?> mappedClass, Map<String, Method> urlToMethod) {
        this.mappedClass = mappedClass;
        this.urlToMethod = urlToMethod;
    }

    public Class<?> getMappedClass() {
        return mappedClass;
    }

    public Map<String, Method> getUrlToMethod() {
        return urlToMethod;
    }
}
