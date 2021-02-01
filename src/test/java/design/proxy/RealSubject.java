package design.proxy;

/**
 * Created on 2020-11-12
 */
public class RealSubject implements Subject{
    @Override
    public void operation() {
        System.out.println("RealSubject");
    }
}
