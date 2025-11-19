package framework.views;

import java.util.HashMap;

public class ModelView {
    private String view;
    private HashMap<String, Object> data = new HashMap<>();

    public ModelView(String view) {
        this.view = view;
    }
    public ModelView() {
    }
    public String getView() {
        return view;
    }
    public void setView(String view) {
        this.view = view;
    }

    public HashMap<String, Object> getData() {
        return data;
    }

    public void setData(HashMap<String, Object> data) {
        this.data = data;
    }

    public void addData(String key, Object value) {
        this.data.put(key, value);
    }
    
}