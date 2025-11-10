package framework.util;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import framework.annotations.Controller;
import framework.annotations.Url;

public class UrlScanner {
    public static class ScanResult {
        public final List<UrlMapping> urlMappings = new ArrayList<>();
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

                    String base = deriveControllerBase(cls);
                    Set<String> seen = new HashSet<>();

                    for (Method m : cls.getDeclaredMethods()) {
                        String path = null;

                        // méthode explicitement annotée @Url -> on enregistre toujours
                        if (m.isAnnotationPresent(Url.class)) {
                            Url u = m.getAnnotation(Url.class);
                            path = u.value();
                        }
                        // si la classe est un @Controller, on génère des paths à partir du nom de la méthode
                        else if (cls.isAnnotationPresent(Controller.class)) {
                            String action = m.getName();
                            if ("index".equals(action)) {
                                path = base;
                            } else {
                                path = base.endsWith("/") ? base + action : base + "/" + action;
                            }
                        }

                        if (path == null) continue;
                        if (!path.startsWith("/")) path = "/" + path;
                        path = path.toLowerCase();

                        // éviter doublons
                        if (seen.contains(path)) continue;
                        seen.add(path);

                        // ajouter mapping individuel (url -> méthode)
                        result.urlMappings.add(new UrlMapping(path, m));
                    }

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