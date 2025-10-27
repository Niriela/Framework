package framework;

import framework.annotations.Controller;
import test.testController.*;

public class Main {
    public static void main(String[] args) {
        Object controller1 = new test1Controller();
        verifierController(controller1);
        Object controller2 = new test2Controller();
        verifierController(controller2);
        Object controller3 = new test3Controller();
        verifierController(controller3);
    }

    public static void verifierController(Object controller) {
        Class<?> controllerClass = controller.getClass();
        boolean hasAnnotation = controllerClass.isAnnotationPresent(Controller.class);
        if (hasAnnotation) {
            System.out.println("The class " + controllerClass.getSimpleName() + " est annote avec @Controller.");
        } else {
            System.out.println("The class " + controllerClass.getSimpleName() + " n'est pas annote avec @Controller.");
        }
    }
}