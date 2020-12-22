package org.apache.ibatis.reflection.typeparam;

import java.util.HashMap;

/**
 * Created on 2020-11-08
 */
public class Level0Test<K, V> {
  protected HashMap<K, V> hashMap;

  public HashMap<K, V> getHashMap() {
    return hashMap;
  }

  public void setHashMap(HashMap<K, V> hashMap) {
    this.hashMap = hashMap;
  }
}
