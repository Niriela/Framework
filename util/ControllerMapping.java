package framework.util;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Représente le mapping d'une classe contrôleur : classe + ses méthodes mappées par URL.
 */
public class ControllerMapping {
    private final Class<?> controllerClass;
    private final Map<String, Method> urlToMethod;

    public ControllerMapping(Class<?> controllerClass, Map<String, Method> urlToMethod) {
        this.controllerClass = controllerClass;
        this.urlToMethod = urlToMethod;
    }

    public Class<?> getControllerClass() {
        return controllerClass;
    }

    public Map<String, Method> getUrlToMethod() {
        return urlToMethod;
    }
}
