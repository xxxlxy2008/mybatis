package cglib.demo;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

public class CglibProxyDemo implements MethodInterceptor {
    // cglib中的Enhancer对象
    private Enhancer enhancer = new Enhancer();

    public Object getProxy(Class clazz) {
        // 代理类的父类
        enhancer.setSuperclass(clazz);
        // 添加Callback对象
        enhancer.setCallback(this);
        // 通过cglib动态创建子类实例并返回
        return enhancer.create();
    }

    // intercept()方法中实现了方法拦截
    public Object intercept(Object obj, Method method, Object[] args,
            MethodProxy proxy) throws Throwable {
        System.out.println("before operation...");
        // 调用父类中的方法
        Object result = proxy.invokeSuper(obj, args);
        System.out.println("after operation...");
        return result;
    }
}