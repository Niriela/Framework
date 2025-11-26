package framework.util;

import jakarta.servlet.http.HttpServletRequest;
import framework.annotations.*;
import java.lang.reflect.Method;
import java.util.List;

public class UrlMatcher {
    
    public static UrlMapping findMapping(String path, String httpMethod, List<UrlMapping> mappings, HttpServletRequest req) {
        String normPath = path.toLowerCase();
        
        System.out.println("=== RECHERCHE MAPPING ===");
        System.out.println("Path: " + path + " | Méthode: " + httpMethod);
        
        // 1) URLs exactes GetMapping/PostMapping
        UrlMapping result = findExactHttpMapping(normPath, httpMethod, mappings);
        if (result != null) return result;
        
        // 2) URLs dynamiques GetMapping/PostMapping
        result = findDynamicHttpMapping(normPath, httpMethod, mappings, path, req);
        if (result != null) return result;
        
        // 3) URLs exactes @Url
        result = findExactUrlMapping(normPath, mappings);
        if (result != null) return result;
        
        // 4) URLs dynamiques @Url
        result = findDynamicUrlMapping(normPath, mappings, path, req);
        if (result != null) return result;
        
        System.out.println("AUCUN MAPPING TROUVÉ\n");
        return null;
    }
    
    private static UrlMapping findExactHttpMapping(String normPath, String httpMethod, List<UrlMapping> mappings) {
        System.out.println("\n--- URLs exactes GetMapping/PostMapping ---");
        for (UrlMapping mapping : mappings) {
            String pattern = normalizePattern(mapping.getUrl());
            Method m = mapping.getMethod();
            
            if (!pattern.contains("{") && pattern.equals(normPath)) {
                if (matchesHttpMethod(m, httpMethod)) {
                    System.out.println("  ✓✓ TROUVÉ!");
                    return mapping;
                }
            }
        }
        return null;
    }
    
    private static UrlMapping findDynamicHttpMapping(String normPath, String httpMethod, List<UrlMapping> mappings, String path, HttpServletRequest req) {
        System.out.println("\n--- URLs dynamiques GetMapping/PostMapping ---");
        for (UrlMapping mapping : mappings) {
            String pattern = normalizePattern(mapping.getUrl());
            Method m = mapping.getMethod();
            
            if (pattern.contains("{")) {
                String regex = pattern.replaceAll("\\{[^/]+\\}", "([^/]+)");
                if (normPath.matches(regex)) {
                    extractPathParams(mapping.getUrl(), path, req);
                    if (matchesHttpMethod(m, httpMethod)) {
                        System.out.println("  ✓✓ TROUVÉ!");
                        return mapping;
                    }
                }
            }
        }
        return null;
    }
    
    private static UrlMapping findExactUrlMapping(String normPath, List<UrlMapping> mappings) {
        System.out.println("\n--- URLs exactes @Url ---");
        for (UrlMapping mapping : mappings) {
            String pattern = normalizePattern(mapping.getUrl());
            Method m = mapping.getMethod();
            
            if (!pattern.contains("{") && pattern.equals(normPath) && m.isAnnotationPresent(Url.class)) {
                System.out.println("  ✓✓ TROUVÉ!");
                return mapping;
            }
        }
        return null;
    }
    
    private static UrlMapping findDynamicUrlMapping(String normPath, List<UrlMapping> mappings, String path, HttpServletRequest req) {
        System.out.println("\n--- URLs dynamiques @Url ---");
        for (UrlMapping mapping : mappings) {
            String pattern = normalizePattern(mapping.getUrl());
            Method m = mapping.getMethod();
            
            if (pattern.contains("{") && m.isAnnotationPresent(Url.class)) {
                String regex = pattern.replaceAll("\\{[^/]+\\}", "([^/]+)");
                if (normPath.matches(regex)) {
                    extractPathParams(mapping.getUrl(), path, req);
                    System.out.println("  ✓✓ TROUVÉ!");
                    return mapping;
                }
            }
        }
        return null;
    }
    
    private static boolean matchesHttpMethod(Method m, String httpMethod) {
        if ("GET".equals(httpMethod) && m.isAnnotationPresent(GetMapping.class)) return true;
        if ("POST".equals(httpMethod) && m.isAnnotationPresent(PostMapping.class)) return true;
        return false;
    }
    
    private static String normalizePattern(String pattern) {
        if (!pattern.startsWith("/")) pattern = "/" + pattern;
        return pattern.toLowerCase();
    }
    
    private static void extractPathParams(String pattern, String actualPath, HttpServletRequest req) {
        String[] patternParts = pattern.split("/");
        String[] pathParts = actualPath.split("/");
        
        System.out.println("  Extraction params: " + pattern + " <- " + actualPath);
        
        for (int i = 0; i < patternParts.length && i < pathParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                String paramName = patternParts[i].substring(1, patternParts[i].length() - 1);
                String paramValue = pathParts[i];
                System.out.println("    -> " + paramName + " = " + paramValue);
                req.setAttribute(paramName, paramValue);
            }
        }
    }
}
