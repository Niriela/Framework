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
import framework.util.ParamConverter;
import framework.views.ModelView; // added import

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
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length()).toLowerCase();

        // Vérifier si la ressource existe
        boolean ressources = getServletContext().getResource(path) != null;

        // Si c'est la racine, afficher un message personnalisé
        if ("/".equals(path)) {
            res.setContentType("text/html");
            try (PrintWriter out = res.getWriter()) {
                out.println("<ml><body>");
                out.println("<h1>Path: /</h1>");
                out.println("</body></html>");
            }
            return;
        } else if (ressources) {
            // Si c'est une ressource statique existante, la servir avec le default
            // dispatcher
            RequestDispatcher rd = getServletContext().getNamedDispatcher("default");
            if (rd != null) {
                rd.forward(req, res);
                return;
            }
        }

        // Chercher un contrôleur pour cette URL
        Method m = null;
        UrlMapping matchedMapping = findUrlMapping(path, req);
        if (matchedMapping != null) {
            m = matchedMapping.getMethod();
        }

        if (m != null) {
            if (handleMappedMethod(req, res, m))
                return;
        }

        // Si rien ne correspond, afficher un message personnalisé
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
                // Injection automatique des paramètres du formulaire
                Object[] args = buildMethodArgs(req, m);
                result = m.invoke(target, args);
            }

            // si ModelView -> forward directement (SANS écrire avant)
            if (result instanceof ModelView) {
                handleModelView(req, res, (ModelView) result);
                return true;
            }

            // sinon on écrit du texte -> là on fixe text/plain ET on écrit le debug +
            // résultat
            try (PrintWriter out = res.getWriter()) {
                res.setContentType("text/plain;charset=UTF-8");
                // vérifier si la classe est annotée @Controller
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
        // si la méthode a retourné une String (extrait demandé)
        if (result instanceof String) {
            out.printf("Methode string invoquee : %s", (String) result);
            return;
        }

        // fallback : ancien comportement sur le type de retour
        Class<?> returnType = m.getReturnType();
        if (returnType == String.class) {
            if (result != null) {
                out.println(result.toString());
            } else {
                out.println("Retour null");
            }
        } else {
            out.println("le type de retour n'est pas string ni ModelView");
        }
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
        for (UrlMapping mapping : scanResult.urlMappings) {
            String urlPattern = mapping.getUrl();
            // Si le pattern contient {param}
            if (urlPattern.contains("{")) {
                // Convertir le pattern en regex
                String regex = urlPattern.replaceAll("\\{[^/]+\\}", "([^/]+)");
                if (path.matches(regex)) {
                    // Extraire le paramètre
                    String[] patternParts = urlPattern.split("/");
                    String[] pathParts = path.split("/");
                    for (int i = 0; i < patternParts.length; i++) {
                        if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                            String paramName = patternParts[i].substring(1, patternParts[i].length() - 1);
                            req.setAttribute(paramName, pathParts[i]);
                        }
                    }
                    return mapping;
                }
            } else if (urlPattern.equals(path)) {
                return mapping;
            }
        }
        return null;
    }

}
