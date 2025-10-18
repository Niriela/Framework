package framework;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontServlet extends HttpServlet {
     @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html");
        
        String path = request.getRequestURI().substring(request.getContextPath().length());

        boolean ressources = getServletContext().getResource(path) != null;

        // Si c'est la racine, on affiche toujours le HTML personnalisé
        if ("/".equals(path)) {
            response.getWriter().println("<html><body>");
            response.getWriter().println("<h1>Path: /</h1>");
            response.getWriter().println("</body></html>");
        } else if (ressources) {
            getServletContext().getNamedDispatcher("default").forward(request, response);
            return;
        } else {
            response.getWriter().println("<html><body>");
            response.getWriter().println("<h1>Path: " + path + "</h1>");
            response.getWriter().println("</body></html>");
        }
    }    
}