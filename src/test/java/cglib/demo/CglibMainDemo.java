package cglib.demo;

public class CglibMainDemo { // 父类，也是代理的目标类
    public String method(String str) { // 被代理的目标方法
        System.out.println(str);
        return "CglibMainDemo:" + str;
    }

    public static void main(String[] args) {
        CglibProxyDemo proxy = new CglibProxyDemo();
        // 获取CglibMainDemo的代理对象
        CglibMainDemo proxyImp = (CglibMainDemo) proxy.getProxy(CglibMainDemo.class);
        // 执行代理对象的method()方法
        String result = proxyImp.method("test");
        System.out.println(result);
    }
}
