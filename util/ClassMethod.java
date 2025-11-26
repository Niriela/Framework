package framework.util;

import java.lang.reflect.Method;
import java.util.HashMap;

public class ClassMethod {
    private Class<?> classe;
    private HashMap<String, Method> getMethods;
    private HashMap<String, Method> postMethods;

    public ClassMethod() {
        this.getMethods = new HashMap<>();
        this.postMethods = new HashMap<>();
    }

    public ClassMethod(Class<?> classe, Method method) {
        this.classe = classe;
        this.getMethods = new HashMap<>();
        this.postMethods = new HashMap<>();
    }

    public Class<?> getClasse() {
        return classe;
    }

    public void setClasse(Class<?> c) {
        this.classe = c;
    }

    public HashMap<String, Method> getGetMethods() {
        return getMethods;
    }

    public HashMap<String, Method> getPostMethods() {
        return postMethods;
    }

    public void addGetMethod(String url, Method method) {
        getMethods.put(url, method);
    }

    public void addPostMethod(String url, Method method) {
        postMethods.put(url, method);
    }

    public Method getGetMethod(String url) {
        return getMethods.get(url);
    }

    public Method getPostMethod(String url) {
        return postMethods.get(url);
    }
}
