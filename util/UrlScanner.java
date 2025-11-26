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
import framework.annotations.GetMapping;
import framework.annotations.PostMapping;

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

    // Nouvelle fonction : vérifie si un pattern contient des paramètres
    private static boolean hasPathParam(String urlPattern) {
        return urlPattern != null && urlPattern.matches(".*\\{[^/]+\\}.*");
    }

    // Nouvelle fonction : convertit un pattern en regex
    private static String patternToRegex(String urlPattern) {
        return urlPattern.replaceAll("\\{[^/]+\\}", "[^/]+");
    }

    // Nouvelle fonction : extrait les noms de paramètres du pattern
    private static List<String> extractParamNames(String urlPattern) {
        List<String> params = new ArrayList<>();
        String[] parts = urlPattern.split("/");
        for (String part : parts) {
            if (part.startsWith("{") && part.endsWith("}")) {
                params.add(part.substring(1, part.length() - 1));
            }
        }
        return params;
    }

    private static void scanDir(File root, File current, ClassLoader loader, ScanResult result) {
        File[] children = current.listFiles();
        if (children == null) return;

        for (File f : children) {
            if (f.isDirectory()) {
                scanDir(root, f, loader, result);
            } else if (f.getName().endsWith(".class")) {
                String rel = root.toURI().relativize(f.toURI()).getPath();
                if (rel.contains("$")) continue;
                String fqcn = rel.replace('/', '.').replace('\\', '.');
                fqcn = fqcn.substring(0, fqcn.length() - ".class".length());
                try {
                    Class<?> cls = loader.loadClass(fqcn);

                    String base = deriveControllerBase(cls);
                    Set<String> seen = new HashSet<>();

                    for (Method m : cls.getDeclaredMethods()) {
                        String path = null;
                        String httpMethod = "ANY"; // par défaut

                        if (m.isAnnotationPresent(Url.class)) {
                            Url u = m.getAnnotation(Url.class);
                            path = u.value();
                            httpMethod = "ANY";
                        } else if (m.isAnnotationPresent(GetMapping.class)) {
                            GetMapping u = m.getAnnotation(GetMapping.class);
                            path = u.value();
                            httpMethod = "GET";
                        } else if (m.isAnnotationPresent(PostMapping.class)) {
                            PostMapping u = m.getAnnotation(PostMapping.class);
                            path = u.value();
                            httpMethod = "POST";
                        } else if (cls.isAnnotationPresent(framework.annotations.Controller.class)) {
                            String action = m.getName();
                            if ("index".equals(action)) {
                                path = base;
                            } else {
                                path = base.endsWith("/") ? base + action : base + "/" + action;
                            }
                            httpMethod = "ANY";
                        }

                        if (path == null) continue;
                        if (!path.startsWith("/")) path = "/" + path;
                        path = path.toLowerCase();

                        String mappingKey = httpMethod + ":" + path;
                        if (seen.contains(mappingKey)) continue;
                        seen.add(mappingKey);

                        UrlMapping mapping = new UrlMapping(path, m);
                        if (hasPathParam(path)) {
                            mapping.setRegex(patternToRegex(path));
                            mapping.setParamNames(extractParamNames(path));
                        }
                        result.urlMappings.add(mapping);
                    }

                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // ignore
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