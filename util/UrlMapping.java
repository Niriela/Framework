package framework.util;

import java.lang.reflect.Method;

/**
 * Représente un mapping d'URL vers une méthode (entrée unique).
 * Plus générique : la méthode peut retourner n'importe quel type.
 */
public class UrlMapping {
    public final String url;
    public final Method method;
    public final Class<?> mappedClass;

    // Constructeur principal utilisé par UrlScanner
    public UrlMapping(String url, Method method) {
        this.url = url;
        this.method = method;
        this.mappedClass = method != null ? method.getDeclaringClass() : null;
    }

    // utilitaire : getters au besoin
    public String getUrl() {
        return url;
    }

    public Method getMethod() {
        return method;
    }

    public Class<?> getMappedClass() {
        return mappedClass;
    }
}