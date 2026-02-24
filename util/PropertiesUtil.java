package framework.util;

import java.io.InputStream;
import java.util.Properties;

public class PropertiesUtil {
    private static final Properties props = new Properties();

    static {
        try (InputStream is = PropertiesUtil.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                // Log ou gestion d'erreur si le fichier n'existe pas
                System.err.println("application.properties non trouvé dans le classpath.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}
