package javassist.demo;


import java.lang.reflect.Method;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;

public class JavassitMainDemo {

    public static void main(String[] args) throws Exception {
        // 创建ProxyFactory工厂实例，它负责动态生成JavassistDemo的子类
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(JavassistDemo.class);
        // 设置Filter，用于确定哪些方法调用需要被代理
        factory.setFilter(new MethodFilter() {
            public boolean isHandled(Method m) {
                if (m.getName().equals("operation")) {
                    return true;
                }
                return false;
            }
        });
        // 设置拦截处理逻辑，被拦截的方法会执行MethodHandler中的逻辑
        factory.setHandler(new MethodHandler() {
            @Override
            public Object invoke(Object self, Method thisMethod, Method proceed,
                                 Object[] args) throws Throwable {
                System.out.println("before operation");
                Object result = proceed.invoke(self, args);
                System.out.println("operation result:" + result);
                System.out.println("after operation");
                return result;
            }
        });

        // 生成代理类，并根据代理类创建代理对象
        Class<?> c = factory.createClass();
        JavassistDemo javassistDemo = (JavassistDemo) c.newInstance();
        // 执行operation()方法时会被拦截，进而执行代理逻辑
        javassistDemo.operation();
        System.out.println(javassistDemo.getDemoProperty());
    }
}
