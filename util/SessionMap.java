package framework.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Enumeration;
import jakarta.servlet.http.HttpSession;

public class SessionMap implements Map<String, Object> {
    private HttpSession session;
    private Map<String, Object> localMap;

    public SessionMap(HttpSession session) {
        this.session = session;
        this.localMap = new HashMap<>();
        // Charger les données existantes de la session
        Enumeration<String> attrNames = session.getAttributeNames();
        while (attrNames.hasMoreElements()) {
            String attrName = attrNames.nextElement();
            localMap.put(attrName, session.getAttribute(attrName));
        }
    }

    @Override
    public int size() {
        return localMap.size();
    }

    @Override
    public boolean isEmpty() {
        return localMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return localMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return localMap.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return localMap.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        Object oldValue = localMap.put(key, value);
        session.setAttribute(key, value);
        return oldValue;
    }

    @Override
    public Object remove(Object key) {
        Object oldValue = localMap.remove(key);
        session.removeAttribute((String) key);
        return oldValue;
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> m) {
        localMap.putAll(m);
        for (Entry<? extends String, ? extends Object> entry : m.entrySet()) {
            session.setAttribute(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        localMap.clear();
        // Supprimer tous les attributs de session
        Enumeration<String> attrNames = session.getAttributeNames();
        while (attrNames.hasMoreElements()) {
            session.removeAttribute(attrNames.nextElement());
        }
    }

    @Override
    public Set<String> keySet() {
        return localMap.keySet();
    }

    @Override
    public Collection<Object> values() {
        return localMap.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return localMap.entrySet();
    }
}
