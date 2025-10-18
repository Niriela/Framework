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
    private RequestDispatcher defaultDispatcher;
    private static final List<String> INDEX_FILES = Arrays.asList(
        "/index.html", "/index.jsp"
    );

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        // Exemple :
        // /app/ -> path = "/"
        // /app/images/logo.png -> path = "/images/logo.png"
        String path = req.getRequestURI().substring(req.getContextPath().length());

        if (path.equals("/") || path.isEmpty()) {
            // Vérifie la présence d’un fichier index
            String indexPath = findExistingIndex();
            if (indexPath != null) {
                // On le redirige vers ce fichier
                req.getRequestDispatcher(indexPath).forward(req, res);
                return;
            } else {
                // Aucun index trouvé : customServe()
                customServe(req, res);
                return;
            }
        }

        boolean resourceExists = getServletContext().getResource(path) != null;
        if (resourceExists) {
            defaultServe(req, res);
        } else {
            customServe(req, res);
        }
    }

    private String findExistingIndex() {
        for (String index : INDEX_FILES) {
            try {
                if (getServletContext().getResource(index) != null) {
                    return index;
                }
            } catch (MalformedURLException e) {
                throw new RuntimeException("Invalid path in INDEX_FILES: " + index, e);
            }
        }
        return null;
    }


    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            String uri = req.getRequestURI();
            String responseBody = """
                <html>
                    <head><title>Resource Not Found</title></head>
                    <body style="font-family:sans-serif;">
                        <h1>Page d'accueil non trouvee </h1>
                        <p>Aucun fichier <code> index.html</code> ou <code> index.jsp</code> n'a ete trouve.</p>
                        <p>URL demandee : <strong>%s</strong></p>
                    </body>
                </html>
                """.formatted(uri);
            out.println(responseBody);
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}