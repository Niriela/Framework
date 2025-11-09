package framework.util;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import framework.annotations.Controller;
import framework.annotations.Url;

public class ControllerScanner {

    public static class ScanResult {
        public final List<ControllerMapping> controllerMappings = new ArrayList<>();
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

                    // vérifier que la classe est annotée @Controller
                    if (!cls.isAnnotationPresent(Controller.class)) {
                        continue; // ignorer les classes sans @Controller
                    }

                    // construire le mapping pour cette classe (url -> Method)
                    Map<String, Method> classMap = new HashMap<>();
                    String base = deriveControllerBase(cls);

                    for (Method m : cls.getDeclaredMethods()) {
                        String path = null;

                        // si méthode annotée @Url -> utiliser sa valeur
                        if (m.isAnnotationPresent(Url.class)) {
                            Url u = m.getAnnotation(Url.class);
                            path = u.value();
                        } else {
                            // mapping automatique : base (+ /action sauf index)
                            String action = m.getName();
                            if ("index".equals(action)) {
                                path = base;
                            } else {
                                path = base.endsWith("/") ? base + action : base + "/" + action;
                            }
                        }

                        if (path == null) continue;
                        if (!path.startsWith("/")) path = "/" + path;
                        classMap.put(path, m);
                    }

                    // ajouter ControllerMapping pour usage futur
                    result.controllerMappings.add(new ControllerMapping(cls, classMap));

                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // ignore classes non disponibles au runtime
                }
            }
        }
    }

    private static String deriveControllerBase(Class<?> cls) {
        String name = cls.getSimpleName();
        if (name.endsWith("Controller")) {
            name = name.substring(0, name.length() - "Controller".length());
        }
        return "/" + name.toLowerCase();
    }
}