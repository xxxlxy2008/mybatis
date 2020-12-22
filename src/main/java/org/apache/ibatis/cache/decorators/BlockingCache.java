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
package org.apache.ibatis.cache.decorators;

import java.text.MessageFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * <p>Simple blocking decorator
 *
 * <p>Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * <p>By its nature, this implementation can cause deadlock when used incorrecly.
 *
 * @author Eduardo Macarron
 */
public class BlockingCache implements Cache {

    private long timeout;
    private final Cache delegate;
    private final ConcurrentHashMap<Object, CountDownLatch> locks;

    public BlockingCache(Cache delegate) {
        this.delegate = delegate;
        this.locks = new ConcurrentHashMap<>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    @Override
    public void putObject(Object key, Object value) {
        try {
            // 将查询结果记录到delegate缓存
            delegate.putObject(key, value);
        } finally {
            // 释放当前key关联的锁
            releaseLock(key);
        }
    }

    @Override
    public Object getObject(Object key) {
        acquireLock(key); // 获取查询key关联的锁
        // 查询delegate缓存
        Object value = delegate.getObject(key);
        if (value != null) {
            // 查询缓存成功，释放锁
            releaseLock(key);
        }
        // 查询失败，不会释放锁，返回null
        return value;
    }

    @Override
    public Object removeObject(Object key) {
        releaseLock(key); // 释放锁
        return null;
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    private void acquireLock(Object key) {
        // 初始化一个全新的CountDownLatch对象
        CountDownLatch newLatch = new CountDownLatch(1);
        while (true) {
            // 尝试将key与newLatch这个CountDownLatch对象关联起来
            // 如果没有其他线程并发，则返回的latch为null
            CountDownLatch latch = locks.putIfAbsent(key, newLatch);
            if (latch == null) {
                // 如果当前key已关联CountDownLatch，
                // 则无其他线程并发，当前线程获取锁成功
                break;
            }
            try {
                // 当前key已关联CountDownLatch对象，则表示有其他线程并发操作当前key，
                // 当前线程需要阻塞在并发线程留下的CountDownLatch对象(latch)之上，
                // 直至并发线程调用latch.countDown()唤醒该线程
                if (timeout > 0) { // 根据timeout的值，决定阻塞超时时间
                    boolean acquired = latch.await(timeout, TimeUnit.MILLISECONDS);
                    if (!acquired) { // 超时未获取到锁，则抛出异常
                        throw new CacheException(
                                "Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
                    }
                } else { // 死等
                    latch.await();
                }
            } catch (InterruptedException e) {
                throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
            }
        }
    }

    private void releaseLock(Object key) {
        // 从locks集合中删除当前Key关联的CountDownLatch对象
        CountDownLatch latch = locks.remove(key);
        if (latch == null) {
            throw new IllegalStateException("Detected an attempt at releasing unacquired lock. This should never happen.");
        }
        // 唤醒阻塞在该CountDownLatch对象上的线程
        latch.countDown();
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
