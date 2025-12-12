package framework.util;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Utilitaire pour binder les paramètres HTTP aux objets avec notation pointée.
 * Exemple: etudiant.notes.moyenne -> remplit l'objet Etudiant
 */
public class ObjectBinder {

    /**
     * Construit les arguments de méthode en bindant les paramètres HTTP aux objets
     * @param paramTypes Types des paramètres de la méthode
     * @param params Paramètres de la méthode (avec noms)
     * @param parameterMap Map des paramètres HTTP (clé=nom, valeur=valeurs)
     * @return Tableau d'objets à passer à la méthode
     */
    public static Object[] bindParameters(Class<?>[] paramTypes, Parameter[] params, Map<String, String[]> parameterMap) {
        Object[] args = new Object[paramTypes.length];

        // Créer une Map de tous les paramètres en une seule clé-valeur
        Map<String, Object> flatParams = flattenParameterMap(parameterMap);

        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = params[i].getName();
            Class<?> paramType = paramTypes[i];

            // Si c'est un type primitif ou String, chercher directement
            if (isPrimitiveOrString(paramType)) {
                args[i] = getDirectValue(paramName, paramType, flatParams);
            } else {
                // Vérifier s'il y a des données pour cet objet (notation pointée)
                boolean hasDataForObject = hasDataForObject(paramName, flatParams);
                
                if (hasDataForObject) {
                    // C'est un objet custom avec notation pointée, créer et remplir
                    args[i] = createAndPopulateObject(paramName, paramType, flatParams);
                } else {
                    // Pas de donnée pour cet objet, retourner null (fallback au système ancien)
                    args[i] = null;
                }
            }
        }

        return args;
    }

    /**
     * Vérifie s'il y a des paramètres avec la notation pointée pour cet objet
     * Exemple: si paramName="etudiant", cherche "etudiant.*"
     */
    private static boolean hasDataForObject(String paramName, Map<String, Object> params) {
        String prefix = paramName + ".";
        for (String key : params.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Transforme Map<String, String[]> en Map<String, Object> simple
     */
    private static Map<String, Object> flattenParameterMap(Map<String, String[]> parameterMap) {
        Map<String, Object> result = new HashMap<>();
        for (String key : parameterMap.keySet()) {
            String[] values = parameterMap.get(key);
            result.put(key, values.length == 1 ? values[0] : values);
        }
        return result;
    }

    /**
     * Crée un objet et le remplit avec les paramètres correspondants
     * Exemple: si paramName="etudiant", cherche "etudiant.notes.moyenne"
     */
    private static Object createAndPopulateObject(String paramName, Class<?> objectClass, Map<String, Object> params) {
        try {
            // Créer l'instance vide
            Object instance = objectClass.getDeclaredConstructor().newInstance();

            // Chercher tous les paramètres commençant par "paramName."
            String prefix = paramName + ".";
            for (String fullKey : params.keySet()) {
                if (fullKey.startsWith(prefix)) {
                    String path = fullKey.substring(prefix.length()); // ex: "notes.moyenne"
                    Object value = params.get(fullKey);
                    
                    // Setter la valeur imbriquée
                    setNestedValue(instance, path, value);
                }
            }

            return instance;
        } catch (Exception e) {
            System.err.println("Erreur création objet " + objectClass.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Set une valeur imbriquée. Exemple: setNestedValue(etudiant, "notes.moyenne", "15.5")
     * Crée les objets intermédiaires si nécessaire
     */
    private static void setNestedValue(Object obj, String path, Object value) {
        String[] parts = path.split("\\.");
        Object current = obj;

        // Naviguer jusqu'à l'avant-dernier attribut
        for (int i = 0; i < parts.length - 1; i++) {
            String attributeName = parts[i];
            Object nextObj = getAttributeValue(current, attributeName);

            // Si l'attribut est null, créer une instance
            if (nextObj == null) {
                Class<?> attributeType = getAttributeType(current, attributeName);
                if (attributeType != null) {
                    try {
                        nextObj = attributeType.getDeclaredConstructor().newInstance();
                        setAttributeValue(current, attributeName, nextObj);
                    } catch (Exception e) {
                        System.err.println("Erreur création attribut " + attributeName + ": " + e.getMessage());
                        return;
                    }
                }
            }

            current = nextObj;
            if (current == null) return;
        }

        // Set la valeur finale
        String lastAttribute = parts[parts.length - 1];
        setAttributeValue(current, lastAttribute, value);
    }

    /**
     * Récupère la valeur d'un attribut en utilisant le getter
     */
    private static Object getAttributeValue(Object obj, String attributeName) {
        try {
            String getterName = "get" + capitalize(attributeName);
            Method getter = findMethod(obj.getClass(), getterName);
            if (getter != null) {
                return getter.invoke(obj);
            }
        } catch (Exception e) {
            // Ignorer
        }
        return null;
    }

    /**
     * Set la valeur d'un attribut en utilisant le setter
     */
    private static void setAttributeValue(Object obj, String attributeName, Object value) {
        try {
            String setterName = "set" + capitalize(attributeName);
            Class<?> targetType = getAttributeType(obj, attributeName);

            if (targetType != null) {
                // Convertir la valeur si nécessaire
                Object convertedValue = convertValue(value, targetType);

                Method setter = obj.getClass().getMethod(setterName, targetType);
                setter.invoke(obj, convertedValue);
            }
        } catch (NoSuchMethodException e) {
            System.err.println("Setter non trouvé: " + obj.getClass().getName() + "." + attributeName);
        } catch (Exception e) {
            System.err.println("Erreur set attribut " + attributeName + ": " + e.getMessage());
        }
    }

    /**
     * Récupère le type d'un attribut en cherchant le getter
     */
    private static Class<?> getAttributeType(Object obj, String attributeName) {
        try {
            String getterName = "get" + capitalize(attributeName);
            Method getter = findMethod(obj.getClass(), getterName);
            if (getter != null) {
                return getter.getReturnType();
            }

            // Sinon chercher le setter
            String setterName = "set" + capitalize(attributeName);
            for (Method m : obj.getClass().getMethods()) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                    return m.getParameterTypes()[0];
                }
            }
        } catch (Exception e) {
            // Ignorer
        }
        return null;
    }

    /**
     * Cherche une méthode dans la classe (cas-insensible pour compatibilité)
     */
    private static Method findMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Récupère une valeur directe (primitive ou String)
     */
    private static Object getDirectValue(String paramName, Class<?> targetType, Map<String, Object> params) {
        Object value = params.get(paramName);
        if (value != null) {
            return convertValue(value, targetType);
        }
        return null;
    }

    /**
     * Convertit une valeur au type cible
     */
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;

        // Si c'est déjà du bon type
        if (targetType.isInstance(value)) {
            return value;
        }

        String strValue = value.toString().trim();

        // Conversions primitives
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(strValue);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(strValue);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(strValue);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(strValue);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(strValue);
        } else if (targetType == String.class) {
            return strValue;
        }

        // Sinon, retourner la valeur brute
        return value;
    }

    /**
     * Vérifie si c'est un type primitif ou String
     */
    private static boolean isPrimitiveOrString(Class<?> type) {
        return type.isPrimitive() || type == String.class || type == Integer.class || 
               type == Double.class || type == Float.class || type == Long.class || 
               type == Boolean.class;
    }

    /**
     * Met en majuscule la première lettre
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
