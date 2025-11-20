package framework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour mapper les paramètres de requête HTTP aux arguments de méthode.
 * Exemple : @Param("id") int identifiant
 * Récupère la valeur du paramètre "id" et la convertit en int.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    String value();
}
