package javassist.demo;

public class JavassistDemo {
    private String demoProperty = "demo-value";

    public String getDemoProperty() {
        return demoProperty;
    }

    public void setDemoProperty(String demoProperty) {
        this.demoProperty = demoProperty;
    }

    public void operation() {
        System.out.println("operation():" + this.demoProperty);
    }
}
