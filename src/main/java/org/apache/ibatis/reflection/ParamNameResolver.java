/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

public class ParamNameResolver {

    public static final String GENERIC_NAME_PREFIX = "param";

    private final boolean useActualParamName;

    /**
     * <p>
     * The key is the index and the value is the name of the parameter.<br />
     * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
     * the parameter index is used. Note that this index could be different from the actual index
     * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
     * </p>
     * <ul>
     * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
     * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
     * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
     * </ul>
     */
    private final SortedMap<Integer, String> names;

    private boolean hasParamAnnotation;

    public ParamNameResolver(Configuration config, Method method) {
        // 从Configuration全局配置中获取useActualParamName配置
        this.useActualParamName = config.isUseActualParamName();
        // 获取参数列表中各个参数的类型
        final Class<?>[] paramTypes = method.getParameterTypes();
        // 获取参数列表上各个参数的注解
        final Annotation[][] paramAnnotations = method.getParameterAnnotations();
        // 用于记录参数索引与参数名称的对应关系
        final SortedMap<Integer, String> map = new TreeMap<>();
        int paramCount = paramAnnotations.length;
        // 下面开始遍历全部参数
        for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
            // 跳过RowBounds类型或ResultHandler类型的参数
            if (isSpecialParameter(paramTypes[paramIndex])) {
                continue;
            }
            String name = null;
            // 遍历当前参数上的全部
            for (Annotation annotation : paramAnnotations[paramIndex]) {
                if (annotation instanceof Param) { // 这里只关注@Param
                    // 只要有一个参数被@Param注解修饰，
                    // 则hasParamAnnotation就会被设置为true
                    hasParamAnnotation = true;
                    // 获取@Param注解指定的参数名称
                    name = ((Param) annotation).value();
                    break;
                }
            }
            if (name == null) {
                if (useActualParamName) {
                    // 如果当前参数没有被@Param注解修饰，
                    // 会根据useActualParamName配置决定是否使用参数
                    // 列表中的变量名称作为其名称
                    name = getActualParamName(method, paramIndex);
                }
                if (name == null) {
                    // useActualParamName配置为false，
                    // 则使用参数下标索引作为其名称
                    name = String.valueOf(map.size());
                }
            }
            // 记录参数下标索引与去名称的映射关系
            map.put(paramIndex, name);
        }
        // 将map集合排序后，赋值给names字段
        names = Collections.unmodifiableSortedMap(map);
    }

    private String getActualParamName(Method method, int paramIndex) {
        return ParamNameUtil.getParamNames(method).get(paramIndex);
    }

    private static boolean isSpecialParameter(Class<?> clazz) {
        return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
    }

    /**
     * Returns parameter names referenced by SQL providers.
     *
     * @return the names
     */
    public String[] getNames() {
        return names.values().toArray(new String[0]);
    }

    /**
     * <p>
     * A single non-special parameter is returned without a name.
     * Multiple parameters are named using the naming rule.
     * In addition to the default names, this method also adds the generic names (param1, param2,
     * ...).
     * </p>
     *
     * @param args
     *          the args
     * @return the named params
     */
    public Object getNamedParams(Object[] args) {
        // 获取方法中非特殊类型(RowBounds类型和ResultHandler类型)的参数个数
        final int paramCount = names.size();
        if (args == null || paramCount == 0) {
            return null; // 方法没有非特殊类型参数，返回null即可
        } else if (!hasParamAnnotation && paramCount == 1) {
            // 方法参数列表中没有使用@Param注解，且只有一个非特殊类型参数
            Object value = args[names.firstKey()];
            return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
        } else {
            // 处理使用存在@Param注解或是存在多个非特殊类型参数的场景
            // param集合用于记录了参数名称与实参之间的映射关系。
            // 这里的ParamMap继承了HashMap，与HashMap的唯一不同是：
            // 向ParamMap中添加已经存在的key时，会直接抛出异常，而不是覆盖原有的Key。
            final Map<String, Object> param = new ParamMap<>();
            int i = 0;
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                // 将参数名称与实参的映射保存到param集合中
                param.put(entry.getValue(), args[entry.getKey()]);
                // 同时，为参数创建"param+索引"格式的默认参数名称，具体格式为：param1, param2等，
                // 将"param+索引"的默认参数名称与实参的映射关系也保存到param集合中
                final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
                if (!names.containsValue(genericParamName)) {
                    param.put(genericParamName, args[entry.getKey()]);
                }
                i++;
            }
            return param;
        }
    }

    /**
     * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
     *
     * @param object a parameter object
     * @param actualParamName an actual parameter name
     *                        (If specify a name, set an object to {@link ParamMap} with specified name)
     * @return a {@link ParamMap}
     * @since 3.5.5
     */
    public static Object wrapToMapIfCollection(Object object, String actualParamName) {
        if (object instanceof Collection) {
            ParamMap<Object> map = new ParamMap<>();
            map.put("collection", object);
            if (object instanceof List) {
                map.put("list", object);
            }
            Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
            return map;
        } else if (object != null && object.getClass().isArray()) {
            ParamMap<Object> map = new ParamMap<>();
            map.put("array", object);
            Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
            return map;
        }
        return object;
    }

}
