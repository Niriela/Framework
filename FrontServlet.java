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
import java.util.Map;
import framework.util.ControllerScanner;

public class FrontServlet extends HttpServlet {
    private RequestDispatcher defaultDispatcher;
    private ControllerScanner.ScanResult scanResult = new ControllerScanner.ScanResult();

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        try {
            scanResult = ControllerScanner.scan(getServletContext());
            // debug : lister mappings
            for (Map.Entry<String, Method> e : scanResult.urlToMethod.entrySet()) {
                System.out.println("Mapped URL: " + e.getKey() + " -> " +
                        e.getValue().getDeclaringClass().getName() + "#" + e.getValue().getName());
            }
        } catch (Exception ex) {
            // conserver scanResult vide en cas d'erreur pour ne pas bloquer le servlet
            scanResult = new ControllerScanner.ScanResult();
            System.err.println("ControllerScanner init error: " + ex.getMessage());
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        boolean resourceExists = getServletContext().getResource(path) != null;
        if (resourceExists) {
            if (defaultDispatcher != null) {
                defaultDispatcher.forward(req, res);
                return;
            }
        }

        Method m = scanResult.urlToMethod.get(path);
        if (m != null) {
            if (handleMappedMethod(req, res, m)) return;
        }

        // fallback : page not found
        customServe(req, res);
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (PrintWriter out = res.getWriter()) {
            String uri = req.getRequestURI();
            String responseBody = "<html><head><title>Resource Not Found</title></head><body>" +
                    "<h1>Unknown resource</h1><p>The requested URL was not found: <strong>" + uri + "</strong></p>" +
                    "</body></html>";
            res.setContentType("text/html;charset=UTF-8");
            out.println(responseBody);
        }
    }

    // nouvelle méthode extrait le comportement d'affichage + invocation
    private boolean handleMappedMethod(HttpServletRequest req, HttpServletResponse res, Method m) throws IOException {
        Class<?> cls = m.getDeclaringClass();
        boolean isController = scanResult.controllerClasses.contains(cls);

        res.setContentType("text/plain;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            if (!isController) {
                out.printf("NON ANNOTEE PAR LE ControllerAnnotation : %s%n", cls.getName());
                return true;
            }

            out.printf("Classe associe : %s%n", cls.getName());
            out.printf("Nom de la methode: %s%n", m.getName());

        }
        return true;   
    }  

}    




