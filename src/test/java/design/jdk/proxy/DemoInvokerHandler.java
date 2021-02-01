package design.jdk.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created on 2021-01-31
 */
public class DemoInvokerHandler implements InvocationHandler {

    private Object target; // 真正的业务对象，也就是RealSubject对象

    // DemoInvokerHandler构造方法
    public DemoInvokerHandler(Object target) {
        this.target = target;
    }

    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
        System.out.println("before"); // 在执行业务逻辑之前的预处理逻辑
        Object result = method.invoke(target, args);
        System.out.println("before"); // 在执行业务逻辑之后的后置处理逻辑
        return result;
    }

    public Object getProxy() {
        // 创建代理对象
        return Proxy.newProxyInstance(Thread.currentThread()
                        .getContextClassLoader(),
                target.getClass().getInterfaces(), this);
    }
}