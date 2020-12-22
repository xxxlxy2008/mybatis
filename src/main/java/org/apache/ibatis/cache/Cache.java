/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * SPI for cache providers.
 * <p>
 * One instance of cache will be created for each namespace.
 * <p>
 * The cache implementation must have a constructor that receives the cache id as an String parameter.
 * <p>
 * MyBatis will pass the namespace as id to the constructor.
 *
 * <pre>
 * public MyCache(final String id) {
 *   if (id == null) {
 *     throw new IllegalArgumentException("Cache instances require an ID");
 *   }
 *   this.id = id;
 *   initialize();
 * }
 * </pre>
 *
 * @author Clinton Begin
 */

public interface Cache {
  // 获取当前缓存的唯一标识
  String getId();

  // 向当前缓存中添加一条缓存数据，key为CacheKey类型对象，value是要缓存的对象
  void putObject(Object key, Object value);

  // 根据指定的key，从当前缓存查找缓存的对象
  Object getObject(Object key);

  // 删除key关联的缓存条目
  Object removeObject(Object key);

  // 清空当前缓存中的全部数据
  void clear();

  // 返回当前缓存中的条目个数
  int getSize();

  // 获取读写锁，该方法不会被MyBatis自身实现使用，默认返回null
  default ReadWriteLock getReadWriteLock() {
    return null;
  }
}
