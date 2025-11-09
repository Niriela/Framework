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
import java.util.Map;
import framework.util.*;

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
                for (Map.Entry<String, Method> e : cm.getUrlToMethod().entrySet()) {
                    System.out.println("Mapped URL: " + e.getKey() + " -> " +
                            e.getValue().getDeclaringClass().getName() + "#" + e.getValue().getName());
                }
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
            // Si c'est une ressource statique existante, la servir avec le default dispatcher
            RequestDispatcher rd = getServletContext().getNamedDispatcher("default");
            if (rd != null) {
                rd.forward(req, res);
                return;
            } 
        }

        // Chercher un contrôleur pour cette URL
        Method m = null;
        for (UrlMapping cm : scanResult.urlMappings) {
            m = cm.getUrlToMethod().get(path);
            if (m != null) break;
        }

        if (m != null) {
            if (handleMappedMethod(req, res, m)) return;
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

        res.setContentType("text/plain;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            // vérifier si la classe est annotée @Controller
            if (!cls.isAnnotationPresent(framework.annotations.Controller.class)) {
                out.printf("classe non annote controller : %s%n", cls.getName());
                return true;
            }

            out.printf("Classe associe : %s%n", cls.getName());
            out.printf("Nom de la methode: %s%n", m.getName());

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
                    // allow method to write directly to response
                    result = m.invoke(target, req, res);
                } else {
                    out.println("Unsupported method signature for invocation");
                    return true;
                }

                // appeler la fonction pour gérer le retour
                handleReturnValue(out, m, result);
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getTargetException();
                out.println("Erreur invocation: " + (cause != null ? cause.toString() : ite.toString()));
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (Exception ex) {
                out.println("Erreur invocation: " + ex.toString());
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
        return true;
    }

    // nouvelle fonction pour gérer le retour de la méthode
    private void handleReturnValue(PrintWriter out, Method m, Object result) {
        Class<?> returnType = m.getReturnType();
        if (returnType == String.class) {
            if (result != null) {
                out.println(result.toString());
            } else {
                out.println("Retour null");
            }
        } else {
            out.println("le type de retour n'est pas string");
        }
    }
}




