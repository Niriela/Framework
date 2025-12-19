package framework;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import framework.util.*;
import framework.views.ModelView;
import framework.annotations.*;


@MultipartConfig(
    maxFileSize = 10485760,      // 10MB
    maxRequestSize = 20971520,    // 20MB
    fileSizeThreshold = 1048576   // 1MB
)

public class FrontServlet extends HttpServlet {
    private UrlScanner.ScanResult scanResult = new UrlScanner.ScanResult();
    // HashMap pour ActionMapping
    private HashMap<String, List<ActionMapping>> actionMappings = new HashMap<>();

    @Override
    public void init() {
        try {
            // Ancien système (pour compatibilité)
            scanResult = UrlScanner.scan(getServletContext());
            
            //  : Utiliser getAllUrl()
            actionMappings = UrlScanner.getAllUrl(getServletContext());
            
            getServletContext().setAttribute("controllerMappings", scanResult.urlMappings);

            // Debug
            System.out.println("=== UrlMapping (ancien) ===");
            for (UrlMapping cm : scanResult.urlMappings) {
                System.out.println("Mapped URL: " + cm.getUrl() + " -> " +
                        cm.getMethod().getDeclaringClass().getName() + "#" + cm.getMethod().getName());
            }
            
            // ActionMapping (nouveau)
            System.out.println("\n=== ActionMapping (nouveau) ===");
            for (String url : actionMappings.keySet()) {
                List<ActionMapping> list = actionMappings.get(url);
                for (ActionMapping am : list) {
                    System.out.println("Mapped URL: " + url + " -> " + 
                            am.getTheClassName() + "#" + am.getTheMethod().getName() + 
                            " [" + am.getHttpMethod() + "]");
                }
            }
            
        } catch (Exception ex) {
            scanResult = new UrlScanner.ScanResult();
            actionMappings = new HashMap<>();
            System.err.println("Scanner init error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        handleRequest(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        handleRequest(req, res);
    }

    private void handleRequest(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String fullUri = req.getRequestURI();
        String context = req.getContextPath();
        String matchPath = fullUri.startsWith(context) ? fullUri.substring(context.length()) : fullUri;
        
        if (matchPath.isEmpty()) matchPath = "/";
        if (matchPath.length() > 1 && matchPath.endsWith("/")) 
            matchPath = matchPath.substring(0, matchPath.length() - 1);

        System.out.println("handleRequest: fullUri=" + fullUri + " context=" + context + " -> matchPath=" + matchPath + " method=" + req.getMethod());

        // Vérifier ressources statiques
        boolean ressources = getServletContext().getResource(matchPath) != null;
        if (ressources) {
            RequestDispatcher rd = getServletContext().getNamedDispatcher("default");
            if (rd != null) rd.forward(req, res);
            return;
        }

        // Essayer d'abord avec ActionMapping (nouveau système)
        ActionMapping actionMapping = findActionMapping(matchPath, req.getMethod(), req);
        if (actionMapping != null) {
            handleActionMapping(req, res, actionMapping);
            return;
        }

        // Fallback : utiliser UrlMatcher (ancien système)
        UrlMapping matchedMapping = UrlMatcher.findMapping(matchPath, req.getMethod(), scanResult.urlMappings, req);
        System.out.println("UrlMatcher retourne: " + (matchedMapping != null ? matchedMapping.getUrl() : "NULL"));
        if (matchedMapping != null) {
            System.out.println("Appel handleMappedMethod...");
            handleMappedMethod(req, res, matchedMapping.getMethod());
        } else {
            System.out.println("Pas de mapping trouvé, customServe...");
            customServe(req, res);
        }
    }

    //  MÉTHODE : Trouver ActionMapping
    private ActionMapping findActionMapping(String path, String httpMethod, HttpServletRequest req) {
        System.out.println("=== RECHERCHE ACTION MAPPING ===");
        System.out.println("Path cherché: " + path + " | HTTP Method: " + httpMethod);

        // 1) Correspondance exacte (sans normalisation)
        if (actionMappings.containsKey(path)) {
            List<ActionMapping> list = actionMappings.get(path);
            for (ActionMapping am : list) {
                if ("ALL".equals(am.getHttpMethod()) || httpMethod.equals(am.getHttpMethod())) {
                    System.out.println("  ✓✓ TROUVÉ ActionMapping exact : " + path);
                    return am;
                }
            }
        }
        
        // 2) Correspondance dynamique avec {param}
        for (String pattern : actionMappings.keySet()) {
            if (pattern.contains("{")) {
                String regex = pattern.replaceAll("\\{[^/]+\\}", "([^/]+)");
                if (path.matches(regex)) {
                    extractPathParams(pattern, path, req);
                    List<ActionMapping> list = actionMappings.get(pattern);
                    for (ActionMapping am : list) {
                        if ("ALL".equals(am.getHttpMethod()) || httpMethod.equals(am.getHttpMethod())) {
                            System.out.println("  ✓✓ TROUVÉ ActionMapping dynamique : " + pattern);
                            return am;
                        }
                    }
                }
            }
        }
        
        System.out.println("  Aucun ActionMapping trouvé");
        return null;
    }

    //  MÉTHODE : Gérer ActionMapping
    private void handleActionMapping(HttpServletRequest req, HttpServletResponse res, ActionMapping am) throws IOException {
        try {
            Class<?> controllerClass = Class.forName(am.getTheClassName());
            Object controller = controllerClass.getDeclaredConstructor().newInstance();
            Object result = invokeMethod(am.getTheMethod(), req, controller);
            handleReturnValue(res.getWriter(), req, res, am.getTheMethod(), result);
        } catch (Exception ex) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("Erreur invocation ActionMapping: " + ex.toString());
            ex.printStackTrace();
        }
    }

    private void extractPathParams(String pattern, String actualPath, HttpServletRequest req) {
        String[] patternParts = pattern.split("/");
        String[] pathParts = actualPath.split("/");
        
        for (int i = 0; i < patternParts.length && i < pathParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                String paramName = patternParts[i].substring(1, patternParts[i].length() - 1);
                String paramValue = pathParts[i];
                req.setAttribute(paramName, paramValue);
            }
        }
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        res.setContentType("text/html");
        try (PrintWriter out = res.getWriter()) {
            out.println("<html><body>");
            out.println("<h1>Path: " + path + "</h1>");
            out.println("</body></html>");
        }
    }

    private boolean handleMappedMethod(HttpServletRequest req, HttpServletResponse res, Method m) throws IOException {
        Class<?> cls = m.getDeclaringClass();

        try {
            Object result = invokeMethod(m, req, cls.getDeclaredConstructor().newInstance());
            handleReturnValue(res.getWriter(), req, res, m, result);
        } catch (InvocationTargetException ite) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("Erreur invocation: " + ite.getTargetException());
            return false;
        } catch (Exception ex) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("Erreur invocation: " + ex.toString());
            return false;
        }
        return true;
    }

    private void handleReturnValue(PrintWriter out, HttpServletRequest req, HttpServletResponse res, Method m, Object result) 
            throws ServletException, IOException {
        
        Class<?> returnType = m.getReturnType();
        
        // If method annotated with @JSON -> return structured JSON
        if (m.isAnnotationPresent(framework.annotations.JSON.class)) {
            res.setContentType("application/json;charset=UTF-8");
            try (PrintWriter pw = res.getWriter()) {
                framework.views.JSONResponse jr = new framework.views.JSONResponse();

                if (result instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String,Object> map = (java.util.Map<String,Object>) result;
                    jr.setData(map);
                    jr.setCount(map.size());
                } else {
                    // build nested data map from request parameters (dot + bracket notation)
                    Map<String, Object> nested = new java.util.HashMap<>();
                    Map<String, String[]> paramMap = req.getParameterMap();
                    for (String fullKey : paramMap.keySet()) {
                        String[] vals = paramMap.get(fullKey);
                        if (vals == null) continue;
                        Object value = (vals.length == 1) ? vals[0] : vals;
                        putNested(nested, fullKey, value);
                    }
                    jr.setData(nested);
                    jr.setCount(nested.size());
                }

                pw.print(jr.toJson());
                pw.flush();
            } catch (Exception ex) {
                res.setStatus(500);
                res.getWriter().println("{\"status\":\"ERROR\",\"code\":500,\"count\":0,\"data\":{}}");
            }
            return;
        }

        if (result instanceof ModelView) {
            handleModelView(req, res, (ModelView) result);
            return;
        }
        
        res.setContentType("text/plain;charset=UTF-8");
        Class<?> cls = m.getDeclaringClass();
        
        if (!cls.isAnnotationPresent(Controller.class)) {
            out.printf("Classe non annotée @Controller : %s%n", cls.getName());
        } else {
            out.printf("Classe associée : %s%n", cls.getName());
            out.printf("Nom de la méthode: %s%n", m.getName());
            
            if (result instanceof String) {
                out.printf("Méthode string invoquée : %s", result);
            } else if (result != null) {
                out.println(result.toString());
            } else {
                out.println("Retour null");
            }
        }
    }

    // Minimal JSON string escaper
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int)c));
                    } else sb.append(c);
            }
        }
        return sb.toString();
    }

    // Insert value into nested map following dot and bracket notation.
    @SuppressWarnings("unchecked")
    private static void putNested(java.util.Map<String, Object> root, String fullKey, Object value) {
        String[] parts = fullKey.split("\\.");
        java.util.Map<String, Object> current = root;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            // detect array/index: name[] or name[index]
            String name = part;
            Integer idx = null;
            if (part.endsWith("[]")) {
                name = part.substring(0, part.length() - 2);
                idx = -1; // means append or treat as array of values
            } else if (part.contains("[")) {
                int b = part.indexOf('[');
                int e = part.indexOf(']');
                name = part.substring(0, b);
                String inside = part.substring(b + 1, e);
                try { idx = Integer.parseInt(inside); } catch (Exception ex) { idx = null; }
            }

            boolean last = (i == parts.length - 1);

            if (last) {
                // set value
                if (idx == null) {
                    current.put(name, value);
                } else if (idx == -1) {
                    Object existing = current.get(name);
                    java.util.List<Object> list;
                    if (existing instanceof java.util.List) list = (java.util.List<Object>) existing;
                    else if (existing != null && existing.getClass().isArray()) {
                        list = new java.util.ArrayList<>();
                        int len = java.lang.reflect.Array.getLength(existing);
                        for (int k = 0; k < len; k++) list.add(java.lang.reflect.Array.get(existing, k));
                    } else {
                        list = new java.util.ArrayList<>();
                        if (existing != null) list.add(existing);
                    }

                    if (value instanceof String[]) {
                        for (String s : (String[]) value) list.add(s);
                    } else {
                        list.add(value);
                    }
                    current.put(name, list);
                } else {
                    Object existing = current.get(name);
                    java.util.List<Object> list;
                    if (existing instanceof java.util.List) list = (java.util.List<Object>) existing;
                    else {
                        list = new java.util.ArrayList<>();
                        if (existing != null) list.add(existing);
                    }
                    while (list.size() <= idx) list.add(null);
                    list.set(idx, value);
                    current.put(name, list);
                }
            } else {
                Object next = current.get(name);
                if (!(next instanceof java.util.Map)) {
                    java.util.Map<String, Object> newMap = new java.util.HashMap<>();
                    current.put(name, newMap);
                    next = newMap;
                }
                current = (java.util.Map<String, Object>) next;
            }
        }
    }

    private void handleModelView(HttpServletRequest req, HttpServletResponse res, ModelView mv)
            throws ServletException, IOException {
        if (mv == null) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("ModelView null");
            return;
        }

        String view = mv.getView();
        if (view == null || view.isEmpty()) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("Vue invalide dans ModelView");
            return;
        }

        if (mv.getData() != null) {
            mv.getData().forEach(req::setAttribute);
        }

        RequestDispatcher rd = req.getRequestDispatcher(view);
        if (rd == null) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("RequestDispatcher introuvable pour: " + view);
            return;
        }

        rd.forward(req, res);
    }

    private Object[] buildMethodArgs(HttpServletRequest req, Method m) {
        Class<?>[] paramTypes = m.getParameterTypes();
        java.lang.reflect.Parameter[] params = m.getParameters();
        Object[] args = new Object[paramTypes.length];

        // Préparer les Maps pour fichiers
        Map<String, byte[]> fileParams = new HashMap<>();
        Set<String> fileParamNames = new HashSet<>(); // Pour tracker les noms de fichiers
        boolean isMultipart = req.getContentType() != null && 
                             req.getContentType().startsWith("multipart/form-data");
        
        if (isMultipart) {
            try {
                for (Part part : req.getParts()) {
                    if (part.getSubmittedFileName() != null && !part.getSubmittedFileName().isEmpty()) {
                        InputStream inputStream = part.getInputStream();
                        byte[] fileBytes = inputStream.readAllBytes();
                        fileParams.put(part.getName(), fileBytes);
                        fileParamNames.add(part.getName()); // Ajouter le nom à l'ensemble
                        inputStream.close();
                    }
                }
            } catch (Exception e) {
                System.err.println("Erreur lecture fichiers: " + e.getMessage());
            }
        }

        for (int i = 0; i < paramTypes.length; i++) {
            Param paramAnnotation = params[i].getAnnotation(Param.class);
            String paramName = (paramAnnotation != null) ? paramAnnotation.value() : params[i].getName();

            // Vérifier si le paramètre est une Map
            if (paramTypes[i].equals(java.util.Map.class)) {
                // Vérifier le type générique
                Type genericType = params[i].getParameterizedType();
                if (genericType instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType) genericType;
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    
                    if (typeArgs.length == 2 && typeArgs[0].equals(String.class)) {
                        if (typeArgs[1].equals(Object.class)) {
                            // Map<String, Object> pour paramètres normaux (exclure les fichiers)
                            args[i] = convertParametersToMap(req, fileParamNames);
                        } else if (isByteArrayType(typeArgs[1])) {
                            // Map<String, byte[]> pour fichiers
                            if (!isMultipart) {
                                System.err.println("ATTENTION: Méthode attend des fichiers mais formulaire pas en multipart!");
                            }
                            args[i] = fileParams;
                        } else {
                            args[i] = convertParametersToMap(req, fileParamNames);
                        }
                    } else {
                        args[i] = convertParametersToMap(req, fileParamNames);
                    }
                } else {
                    args[i] = convertParametersToMap(req, fileParamNames);
                }
            } else {
                // Utiliser ObjectBinder pour les objets complexes avec notation pointée
                Object bindedValue = ObjectBinder.bindParameters(
                    new Class<?>[] { paramTypes[i] },
                    new java.lang.reflect.Parameter[] { params[i] },
                    req.getParameterMap()
                )[0];
                
                // Si ObjectBinder retourne null, essayer la conversion classique
                if (bindedValue == null) {
                    String paramValue = req.getParameter(paramName);
                    if (paramValue == null) {
                        Object attr = req.getAttribute(paramName);
                        if (attr != null) paramValue = String.valueOf(attr);
                    }

                    if (paramValue != null && !paramValue.isEmpty()) {
                        args[i] = ParamConverter.convert(paramValue, paramTypes[i]);
                    } else {
                        args[i] = null;
                    }
                } else {
                    args[i] = bindedValue;
                }
            }
        }
        return args;
    }

    // Nouvelle méthode pour convertir les paramètres en Map<String, Object>
    private Map<String, Object> convertParametersToMap(HttpServletRequest req, Set<String> fileParamNames) {
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, String[]> parameterMap = req.getParameterMap();
        
        for (String key : parameterMap.keySet()) {
            // Exclure les paramètres qui sont des fichiers
            if (fileParamNames != null && fileParamNames.contains(key)) {
                continue;
            }
            
            String[] values = parameterMap.get(key);
            // Si un seul paramètre, ajouter la valeur directement
            // Sinon, ajouter le tableau
            if (values.length == 1) {
                resultMap.put(key, values[0]);
            } else {
                resultMap.put(key, values);
            }
        }
        
        System.out.println("=== Map des paramètres ===");
        resultMap.forEach((key, value) -> System.out.println(key + " -> " + value));
        
        return resultMap;
    }

    // Vérifie si le type correspond à byte[] pour la détection des maps de fichiers
    private boolean isByteArrayType(Type type) {
        if (type instanceof Class<?>) {
            return ((Class<?>) type).equals(byte[].class);
        }
        String typeName = type.getTypeName();
        return "byte[]".equals(typeName) || "[B".equals(typeName);
    }

    private Object invokeMethod(Method method, HttpServletRequest req, Object controllerInstance) throws Exception {
        method.setAccessible(true);
        Class<?>[] paramTypes = method.getParameterTypes();

        if (paramTypes.length == 0) {
            return method.invoke(controllerInstance);
        }

        if (paramTypes.length == 1 && HttpServletRequest.class.isAssignableFrom(paramTypes[0])) {
            return method.invoke(controllerInstance, req);
        }

        Object[] args = buildMethodArgs(req, method);
        return method.invoke(controllerInstance, args);
    }
}
