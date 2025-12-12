package framework.views;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple container for JSON responses: status, code, count and data map.
 */
public class JSONResponse {
    private String status;
    private int code;
    private int count;
    private Map<String, Object> data;

    public JSONResponse() {
        this.status = "OK";
        this.code = 200;
        this.count = 0;
        this.data = new HashMap<>();
    }

    public JSONResponse(String status, int code, int count, Map<String, Object> data) {
        this.status = status;
        this.code = code;
        this.count = count;
        this.data = (data != null) ? data : new HashMap<>();
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public void addData(String key, Object value) { this.data.put(key, value); }

    public void computeCountFrom(Object result) {
        if (result == null) {
            this.count = 0;
        } else if (result instanceof Collection) {
            this.count = ((Collection<?>) result).size();
        } else if (result.getClass().isArray()) {
            this.count = java.lang.reflect.Array.getLength(result);
        } else {
            this.count = 1;
        }
    }

    /**
     * Serialize to JSON string (small built-in serializer, no external deps).
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"status\":\"").append(escape(status)).append("\"");
        sb.append(',').append("\"code\":").append(code);
        sb.append(',').append("\"count\":").append(count);
        sb.append(',').append("\"data\":");
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append('"').append(':');
            appendValue(sb, e.getValue());
        }
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    private static void appendValue(StringBuilder sb, Object v) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof Number || v instanceof Boolean) {
            sb.append(v.toString());
            return;
        }
        if (v instanceof String) {
            sb.append('"').append(escape((String) v)).append('"');
            return;
        }
        if (v instanceof java.util.Map) {
            sb.append('{');
            java.util.Map<?,?> m = (java.util.Map<?,?>) v;
            boolean first = true;
            for (java.util.Map.Entry<?,?> e : m.entrySet()) {
                if (!first) sb.append(','); first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append('"').append(':');
                appendValue(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (v instanceof String[]) {
            sb.append('[');
            String[] a = (String[]) v;
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escape(a[i])).append('"');
            }
            sb.append(']');
            return;
        }
        if (v.getClass().isArray()) {
            sb.append('[');
            int len = java.lang.reflect.Array.getLength(v);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(',');
                Object item = java.lang.reflect.Array.get(v, i);
                appendValue(sb, item);
            }
            sb.append(']');
            return;
        }
        if (v instanceof java.util.Collection) {
            sb.append('[');
            java.util.Iterator<?> it = ((java.util.Collection<?>) v).iterator();
            boolean first = true;
            while (it.hasNext()) {
                if (!first) sb.append(','); first = false;
                appendValue(sb, it.next());
            }
            sb.append(']');
            return;
        }
        // Fallback: use toString as string
        sb.append('"').append(escape(String.valueOf(v))).append('"');
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') sb.append(String.format("\\u%04x", (int)c)); else sb.append(c);
            }
        }
        return sb.toString();
    }
}
