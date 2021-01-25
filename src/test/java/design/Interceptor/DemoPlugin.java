package design.Interceptor;

import java.util.Properties;
import java.util.concurrent.Executor;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class,
                ResultHandler.class}),
        @Signature(type = Executor.class, method = "close", args = {boolean.class})
})
public class DemoPlugin implements Interceptor {

    private int logLevel;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        System.out.println("before intercept");
        Object proceed = invocation.proceed();
        System.out.println("after intercept");
        return proceed;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {

    }
}
