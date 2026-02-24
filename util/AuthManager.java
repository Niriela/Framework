package framework.util;

import framework.annotations.Authorized;
import framework.annotations.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

public class AuthManager {
    private static final String AUTH_VAR = PropertiesUtil.get("auth.variable");
    private static final String ROLE_VAR = PropertiesUtil.get("role.variable");

    public static boolean isAuthorized(Method method, HttpServletRequest req) {
        // Vérification @Authorized : l'utilisateur doit être authentifié
        if (method.isAnnotationPresent(Authorized.class)) {
            Object authValue = req.getSession().getAttribute(AUTH_VAR);
            if (authValue == null) {
                return false;  // Non authentifié
            }
        }

        // Vérification @Role : l'utilisateur doit être authentifié ET avoir le rôle requis
        if (method.isAnnotationPresent(Role.class)) {
            // D'abord, vérifier l'authentification (nécessaire pour les rôles)
            Object authValue = req.getSession().getAttribute(AUTH_VAR);
            if (authValue == null) {
                return false;  // Non authentifié, donc pas de rôle possible
            }

            Role roleAnnotation = method.getAnnotation(Role.class);
            String requiredRole = roleAnnotation.value();
            Object sessionRole = req.getSession().getAttribute(ROLE_VAR);
            if (sessionRole == null || !requiredRole.equals(sessionRole.toString())) {
                return false;  // Rôle insuffisant
            }
        }

        return true;  // Autorisé
    }
}