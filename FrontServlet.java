package framework;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import framework.util.*;
import framework.views.ModelView; // added import
import framework.annotations.*;; // added import

public class FrontServlet extends HttpServlet {
    private RequestDispatcher defaultDispatcher;
    private UrlScanner.ScanResult scanResult = new UrlScanner.ScanResult();

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        try {
            scanResult = UrlScanner.scan(getServletContext());
            // mettre les mappings dans le ServletContext pour usage par d'autres composants
            getServletContext().setAttribute("controllerMappings", scanResult.urlMappings);

            // debug : lister mappings depuis controllerMappings
            for (UrlMapping cm : scanResult.urlMappings) {
                System.out.println("Mapped URL: " + cm.getUrl() + " -> " +
                        cm.getMethod().getDeclaringClass().getName() + "#" + cm.getMethod().getName());
            }
        } catch (Exception ex) {
            // conserver scanResult vide en cas d'erreur pour ne pas bloquer le servlet
            scanResult = new UrlScanner.ScanResult();
            System.err.println("ControllerScanner init error: " + ex.getMessage());
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
        // Normaliser le path (enlever contextPath)
        String fullUri = req.getRequestURI();
        String context = req.getContextPath();
        String matchPath = fullUri.startsWith(context) ? fullUri.substring(context.length()) : fullUri;
        if (matchPath.isEmpty()) matchPath = "/";
        if (matchPath.length() > 1 && matchPath.endsWith("/")) matchPath = matchPath.substring(0, matchPath.length() - 1);

        System.out.println("handleRequest: fullUri=" + fullUri + " context=" + context + " -> matchPath=" + matchPath + " method=" + req.getMethod());

        // Vérifier si c'est une ressource statique
        boolean ressources = getServletContext().getResource(matchPath) != null;
        if (ressources) {
            RequestDispatcher rd = getServletContext().getNamedDispatcher("default");
            if (rd != null) {
                rd.forward(req, res);
                return;
            }
        }

        // Chercher le mapping avec le path normalisé
        UrlMapping matchedMapping = findUrlMapping(matchPath, req);

        if (matchedMapping != null) {
            if (handleMappedMethod(req, res, matchedMapping.getMethod()))
                return;
        }

        // Fallback si rien ne correspond
        customServe(req, res);
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

    // nouvelle méthode extrait le comportement d'affichage + invocation
    private boolean handleMappedMethod(HttpServletRequest req, HttpServletResponse res, Method m) throws IOException {
        Class<?> cls = m.getDeclaringClass();

        try {
            Object target = Modifier.isStatic(m.getModifiers()) ? null : cls.getDeclaredConstructor().newInstance();
            m.setAccessible(true);

            Class<?>[] params = m.getParameterTypes();
            Object result = null;

            if (params.length == 0) {
                result = m.invoke(target);
            } else if (params.length == 1 && HttpServletRequest.class.isAssignableFrom(params[0])) {
                result = m.invoke(target, req);
            } else if (params.length == 2
                    && HttpServletRequest.class.isAssignableFrom(params[0])
                    && HttpServletResponse.class.isAssignableFrom(params[1])) {
                result = m.invoke(target, req, res);
            } else {
                // Appel de invokeMethod pour gérer les paramètres annotés avec @Param
                result = invokeMethod(m, req, target);
            }

            // si ModelView -> forward directement (SANS écrire avant)
            if (result instanceof ModelView) {
                handleModelView(req, res, (ModelView) result);
                return true;
            }

            // sinon on écrit du texte
            try (PrintWriter out = res.getWriter()) {
                res.setContentType("text/plain;charset=UTF-8");
                if (!cls.isAnnotationPresent(framework.annotations.Controller.class)) {
                    out.printf("classe non annote controller : %s%n", cls.getName());
                } else {
                    out.printf("Classe associe : %s%n", cls.getName());
                    out.printf("Nom de la methode: %s%n", m.getName());
                    handleReturnValue(out, req, res, m, result);
                }
            }

        } catch (InvocationTargetException ite) {
            try (PrintWriter out = res.getWriter()) {
                res.setContentType("text/plain;charset=UTF-8");
                out.println("Erreur invocation: " + ite.getTargetException());
            }
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            try (PrintWriter out = res.getWriter()) {
                res.setContentType("text/plain;charset=UTF-8");
                out.println("Erreur invocation: " + ex.toString());
            }
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return true;
    }

    // nouvelle fonction pour gérer le retour de la méthode
    private void handleReturnValue(PrintWriter out, HttpServletRequest req, HttpServletResponse res, Method m,
            Object result) throws ServletException, IOException {
        
        Class<?> returnType = m.getReturnType();
        
        // Si String
        if (result instanceof String) {
            out.printf("Methode string invoquee : %s", (String) result);
            return;
        }
        
        // Si int, double, float, boolean, etc.
        if (result != null) {
            out.println(result.toString());
            return;
        }
        
        // Si null
        out.println("Retour null");
    }

    private void handleModelView(HttpServletRequest req, HttpServletResponse res, ModelView mv)
            throws ServletException, IOException {
        if (mv == null) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("ModelView est null");
            return;
        }

        String view = mv.getView();
        if (view == null || view.isEmpty()) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("ModelView.view est null ou vide");
            return;
        }

        // Ajout des données du ModelView dans la requête
        if (mv.getData() != null) {
            for (String key : mv.getData().keySet()) {
                req.setAttribute(key, mv.getData().get(key));
            }
        }

        RequestDispatcher rd = req.getRequestDispatcher(view);
        if (rd == null) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("Impossible d'obtenir RequestDispatcher pour la vue: " + view);
            return;
        }

        rd.forward(req, res);
    }

    private Object[] buildMethodArgs(HttpServletRequest req, Method m) {
        Class<?>[] paramTypes = m.getParameterTypes();
        java.lang.reflect.Parameter[] params = m.getParameters();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = params[i].getName(); // nécessite -parameters à la compilation
            String value = req.getParameter(paramName);
            Class<?> t = paramTypes[i];

            args[i] = ParamConverter.convert(value, t);
        }
        return args;
    }
    
    private UrlMapping findUrlMapping(String path, HttpServletRequest req) {
        String httpMethod = req.getMethod();
        String normPath = path.toLowerCase();
        
        System.out.println("=== RECHERCHE MAPPING ===");
        System.out.println("Path demandé: " + path + " (normalisé: " + normPath + ")");
        System.out.println("Méthode HTTP: " + httpMethod);
        System.out.println("Nombre de mappings disponibles: " + scanResult.urlMappings.size());

        // 1) exact Get/Post (non dyn)
        System.out.println("\n--- Étape 1: URLs exactes GetMapping/PostMapping ---");
        for (UrlMapping mapping : scanResult.urlMappings) {
            String urlPattern = mapping.getUrl();
            if (!urlPattern.startsWith("/")) urlPattern = "/" + urlPattern;
            String normPattern = urlPattern.toLowerCase();
            Method m = mapping.getMethod();

            if (!normPattern.contains("{") && normPattern.equals(normPath)) {
                if ("GET".equals(httpMethod) && m.isAnnotationPresent(GetMapping.class)) {
                    System.out.println("  ✓✓ TROUVÉ GetMapping exact!");
                    return mapping;
                }
                if ("POST".equals(httpMethod) && m.isAnnotationPresent(PostMapping.class)) {
                    System.out.println("  ✓✓ TROUVÉ PostMapping exact!");
                    return mapping;
                }
            }
        }

        // 2) dyn Get/Post (patterns with {param})
        System.out.println("\n--- Étape 2: URLs dynamiques GetMapping/PostMapping ---");
        for (UrlMapping mapping : scanResult.urlMappings) {
            String urlPattern = mapping.getUrl();
            if (!urlPattern.startsWith("/")) urlPattern = "/" + urlPattern;
            String normPattern = urlPattern.toLowerCase();
            Method m = mapping.getMethod();

            if (normPattern.contains("{")) {
                String regex = normPattern.replaceAll("\\{[^/]+\\}", "([^/]+)");
                if (normPath.matches(regex)) {
                    //  EXTRAIRE LES PARAMÈTRES ICI
                    extractPathParams(urlPattern, path, req);
                    
                    if ("GET".equals(httpMethod) && m.isAnnotationPresent(GetMapping.class)) {
                        System.out.println("  ✓✓ TROUVÉ GetMapping dynamique!");
                        return mapping;
                    }
                    if ("POST".equals(httpMethod) && m.isAnnotationPresent(PostMapping.class)) {
                        System.out.println("  ✓✓ TROUVÉ PostMapping dynamique!");
                        return mapping;
                    }
                }
            }
        }

        // 3) exact @Url fallback
        System.out.println("\n--- Étape 3: URLs exactes @Url ---");
        for (UrlMapping mapping : scanResult.urlMappings) {
            String urlPattern = mapping.getUrl();
            if (!urlPattern.startsWith("/")) urlPattern = "/" + urlPattern;
            String normPattern = urlPattern.toLowerCase();
            Method m = mapping.getMethod();

            if (!normPattern.contains("{") && normPattern.equals(normPath) && m.isAnnotationPresent(Url.class)) {
                System.out.println("  ✓✓ TROUVÉ @Url exact!");
                return mapping;
            }
        }

        // 4) dyn @Url fallback
        System.out.println("\n--- Étape 4: URLs dynamiques @Url ---");
        for (UrlMapping mapping : scanResult.urlMappings) {
            String urlPattern = mapping.getUrl();
            if (!urlPattern.startsWith("/")) urlPattern = "/" + urlPattern;
            String normPattern = urlPattern.toLowerCase();
            Method m = mapping.getMethod();

            if (normPattern.contains("{") && m.isAnnotationPresent(Url.class)) {
                String regex = normPattern.replaceAll("\\{[^/]+\\}", "([^/]+)");
                System.out.println("Test @Url dynamique: " + urlPattern + " regex=" + regex);
                if (normPath.matches(regex)) {
                    // EXTRAIRE LES PARAMÈTRES ICI AUSSI
                    extractPathParams(urlPattern, path, req);
                    System.out.println("  ✓✓ TROUVÉ @Url dynamique!");
                    return mapping;
                }
            }
        }

        System.out.println("AUCUN MAPPING TROUVÉ\n");
        return null;
    }

    // NOUVELLE MÉTHODE pour extraire les paramètres
    private void extractPathParams(String pattern, String actualPath, HttpServletRequest req) {
        String[] patternParts = pattern.split("/");
        String[] pathParts = actualPath.split("/");
        
        System.out.println("  Extraction params: pattern=" + pattern + " actualPath=" + actualPath);
        
        for (int i = 0; i < patternParts.length && i < pathParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                String paramName = patternParts[i].substring(1, patternParts[i].length() - 1);
                String paramValue = pathParts[i];
                System.out.println("    -> Param extrait: " + paramName + " = " + paramValue);
                req.setAttribute(paramName, paramValue);
            }
        }
    }

    private Object invokeMethod(Method method, HttpServletRequest request, Object controllerInstance) throws Exception {
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            java.lang.reflect.Parameter param = parameters[i];
            Param paramAnnotation = param.getAnnotation(Param.class);
            
            if (paramAnnotation != null) {
                // Gestion de @Param
                String paramName = paramAnnotation.value();
                String paramValue = request.getParameter(paramName);
                
                if (paramValue == null) {
                    Object attr = request.getAttribute(paramName);
                    if (attr != null) paramValue = String.valueOf(attr);
                }
                
                if (paramValue != null && !paramValue.isEmpty()) {
                    Class<?> paramType = param.getType();
                    args[i] = ParamConverter.convert(paramValue, paramType);
                } else {
                    args[i] = null;
                }
            } else {
                // Pas d'annotation @Param : chercher automatiquement par le nom du paramètre
                String paramName = param.getName();
                String paramValue = request.getParameter(paramName);
                
                // Si pas en paramètre de requête, chercher dans les attributs (variables de chemin)
                if (paramValue == null) {
                    Object attr = request.getAttribute(paramName);
                    if (attr != null) paramValue = String.valueOf(attr);
                }
                
                if (paramValue != null && !paramValue.isEmpty()) {
                    Class<?> paramType = param.getType();
                    args[i] = ParamConverter.convert(paramValue, paramType);
                } else {
                    args[i] = null;
                }
            }
        }
        
        return method.invoke(controllerInstance, args);
    }

}
