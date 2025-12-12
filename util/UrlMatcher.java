package framework.util;

import jakarta.servlet.http.HttpServletRequest;
import framework.annotations.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    private static UrlMapping findDynamicHttpMapping(String normPath, String httpMethod, List<UrlMapping> mappings, String path, HttpServletRequest req) {
        System.out.println("\n--- URLs dynamiques GetMapping/PostMapping ---");
        for (UrlMapping mapping : mappings) {
            String pattern = normalizePattern(mapping.getUrl());
            Method m = mapping.getMethod();
            
            if (pattern.contains("{")) {
                String regex = buildRegex(pattern);
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
                String regex = buildRegex(pattern);
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
                String spec = patternParts[i].substring(1, patternParts[i].length() - 1);
                int colonIndex = spec.indexOf(':');
                String paramName = colonIndex >= 0 ? spec.substring(0, colonIndex) : spec;
                String paramValue = pathParts[i];
                System.out.println("    -> " + paramName + " = " + paramValue);
                req.setAttribute(paramName, paramValue);
            }
        }
    }

    private static String buildRegex(String pattern) {
        Matcher matcher = PARAM_PATTERN.matcher(pattern);
        StringBuilder regex = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            regex.append(pattern, lastEnd, matcher.start());

            String spec = matcher.group(1);
            String[] parts = spec.split(":", 2);
            String partRegex = parts.length == 2 ? parts[1] : "[^/]+";
            regex.append("(").append(partRegex).append(")");

            lastEnd = matcher.end();
        }

        regex.append(pattern.substring(lastEnd));
        return regex.toString();
    }
}
