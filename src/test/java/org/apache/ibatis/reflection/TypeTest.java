package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;

import org.apache.ibatis.reflection.typeparam.Level0Test;
import org.checkerframework.checker.units.qual.A;
import org.junit.Test;

/**
 * Created on 2020-11-08
 */
public class TypeTest {

  @Test
  public void testHashMapField() throws NoSuchFieldException {
    Field f = Level0Test.class.getDeclaredField("hashMap");
    System.out.println(f.getGenericType());
    System.out.println(f.getGenericType() instanceof ParameterizedType);
    // 输出是：
    // java.util.HashMap<K, V>
    // true
  }
}
