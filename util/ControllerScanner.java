package framework.util;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import framework.annotations.Controller;
import framework.annotations.Url;

public class ControllerScanner {

    public static class ScanResult {
        public final Map<String, Method> urlToMethod = new HashMap<>();
        public final Set<Class<?>> controllerClasses = new HashSet<>();
    }

    public static ScanResult scan(ServletContext ctx) {
        ScanResult result = new ScanResult();
        if (ctx == null) return result;

        String classesPath = ctx.getRealPath("/WEB-INF/classes");
        if (classesPath == null) return result;

        File root = new File(classesPath);
        if (!root.exists() || !root.isDirectory()) return result;

        scanDir(root, root, ctx.getClassLoader(), result);
        return result;
    }

    private static void scanDir(File root, File current, ClassLoader loader, ScanResult result) {
        File[] children = current.listFiles();
        if (children == null) return;

        for (File f : children) {
            if (f.isDirectory()) {
                scanDir(root, f, loader, result);
            } else if (f.getName().endsWith(".class")) {
                String rel = root.toURI().relativize(f.toURI()).getPath();
                if (rel.contains("$")) continue; // ignore inner / anonymous classes
                String fqcn = rel.replace('/', '.').replace('\\', '.');
                fqcn = fqcn.substring(0, fqcn.length() - ".class".length());
                try {
                    Class<?> cls = loader.loadClass(fqcn);

                    // chercher @Controller (framework.annotations.Controller)
                    if (cls.isAnnotationPresent(Controller.class)) {
                        result.controllerClasses.add(cls);
                    }

                    // chercher @Url sur les méthodes (framework.annotations.Url)
                    for (Method m : cls.getDeclaredMethods()) {
                        if (m.isAnnotationPresent(Url.class)) {
                            Url u = m.getAnnotation(Url.class);
                            String path = u.value();
                            if (path == null) continue;
                            if (!path.startsWith("/")) path = "/" + path;
                            result.urlToMethod.put(path, m);
                        }
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // la classe n'est pas disponible au runtime : ignorer
                }
            }
        }
    }
}