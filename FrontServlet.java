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
import framework.util.*;
import framework.views.ModelView;
import framework.annotations.*;

public class FrontServlet extends HttpServlet {
    private UrlScanner.ScanResult scanResult = new UrlScanner.ScanResult();

    @Override
    public void init() {
        try {
            scanResult = UrlScanner.scan(getServletContext());
            getServletContext().setAttribute("controllerMappings", scanResult.urlMappings);

            for (UrlMapping cm : scanResult.urlMappings) {
                System.out.println("Mapped URL: " + cm.getUrl() + " -> " +
                        cm.getMethod().getDeclaringClass().getName() + "#" + cm.getMethod().getName());
            }
        } catch (Exception ex) {
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

        //  Utiliser UrlMatcher au lieu de findUrlMapping
        UrlMapping matchedMapping = UrlMatcher.findMapping(matchPath, req.getMethod(), scanResult.urlMappings, req);

        if (matchedMapping != null) {
            handleMappedMethod(req, res, matchedMapping.getMethod());
        } else {
            customServe(req, res);
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

        for (int i = 0; i < paramTypes.length; i++) {
            Param paramAnnotation = params[i].getAnnotation(Param.class);
            String paramName = (paramAnnotation != null) ? paramAnnotation.value() : params[i].getName();

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
        }
        return args;
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
