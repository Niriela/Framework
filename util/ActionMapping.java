package framework.util;

import java.lang.reflect.Method;

public class ActionMapping {
    private String theClassName;
    private Method theMethod;
    private String httpMethod;

    public ActionMapping(String theClassName, Method theMethod, String httpMethod) {
        this.theClassName = theClassName;
        this.theMethod = theMethod;
        this.httpMethod = httpMethod;
    }

    public String getTheClassName() {
        return this.theClassName;
    }

    public void setTheClassName(String theClassName) {
        this.theClassName = theClassName;
    }

    public Method getTheMethod() {
        return this.theMethod;
    }

    public void setTheMethod(Method theMethod) {
        this.theMethod = theMethod;
    }

    public String getHttpMethod() {
        return this.httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }
}
