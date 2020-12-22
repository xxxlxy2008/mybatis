package org.apache.ibatis.reflection.typeparam;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.apache.ibatis.reflection.TypeParameterResolver;

import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

public class TypeParameterResolverTest {

  public static Level1Test<Long> longLevel1Test = new Level1Test<>();

  public static void main(String[] args) throws Exception {
    Field f = Level0Test.class.getDeclaredField("hashMap");
    System.out.println(f.getGenericType());
    System.out.println(f.getGenericType() instanceof ParameterizedType);
    // 输出是：
    // java.util.Map<K, V>
    // true

    // 解析Level1Test<Long>（ParameterizedType类型）中的hashMap字段，这里，ParameterizedTypeImpl是
    // 在sun.reflect.generics.reflectiveObjects包下的ParameterizedType接口实现
    Type type = TypeParameterResolver.resolveFieldType(f, ParameterizedTypeImpl
      .make(Level1Test.class, new Type[]{Long.class}, TypeParameterResolverTest.class));
    // 一般情况下，我们是通过下面的方式获取resolveFieldType()方法中的第二个，即ParameterizedType对象
    // TypeParameterResolver.resolveFieldType(f,
    //      TypeParameterResolverTest.class.getDeclaredField("sa").getGenericType());

    TypeParameterResolver.resolveFieldType(f,
      TypeParameterResolverTest.class.getDeclaredField("longLevel1Test").getGenericType());

    System.out.println(type.getClass());
    // 输出：class TypeParameterResolver$ParameterizedTypeImpl
    // 注意，TypeParameterResolver$ParameterizedTypeImpl是ParameterizedType接口的实现
    ParameterizedType p = (ParameterizedType) type;
    System.out.println(p.getRawType());
    // 输出：interface java.util.Map
    System.out.println(p.getOwnerType());
    // 输出：null
    for (Type t : p.getActualTypeArguments()) {
      System.out.println(t);
    }
    // 输出：
    // class java.lang.Long
    // class java.lang.Long
  }
}
