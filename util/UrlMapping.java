package framework.util;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Représente un mapping d'URL vers une méthode (entrée unique).
 * Plus générique : la méthode peut retourner n'importe quel type.
 */
public class UrlMapping {
    public final String url;
    public final Method method;
    public final Class<?> mappedClass;

    private String regex;
    private List<String> paramNames;

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

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getRegex() {
        return regex;
    }

    public void setParamNames(List<String> paramNames) {
        this.paramNames = paramNames;
    }

    public List<String> getParamNames() {
        return paramNames;
    }
}