/**
 *    Copyright 2009-2019 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;

/**
 * TransactionalCacheManager 内部维护了 Cache 实例与 TransactionalCache 实例间的映射关系，该类
 * 也仅负责维护两者的映射关系，真正做事的还是 TransactionalCache。TransactionalCache 是⼀种缓
 * 存装饰器，可以为 Cache 实例增加事务功能。
 *
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

  /**
   * Cache 和 TransactionalCache 的映射
   */
  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  public void clear(Cache cache) {
    // 获取 TransactionalCache 对象，并调⽤该对象的 clear ⽅法，下同
    getTransactionalCache(cache).clear();
  }

  public Object getObject(Cache cache, CacheKey key) {
    // 首先，获得 Cache 对应的 TransactionalCache 对象
    // 然后从 TransactionalCache 对象中，获得 key 对应的值
    return getTransactionalCache(cache).getObject(key);
  }

  public void putObject(Cache cache, CacheKey key, Object value) {
    // 首先，获得 Cache 对应的 TransactionalCache 对象
    // 然后，添加 KV 到 TransactionalCache 对象中
    getTransactionalCache(cache).putObject(key, value);
  }

  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }

  public void rollback() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  private TransactionalCache getTransactionalCache(Cache cache) {
    //// 从映射表中获取 TransactionalCache
    // TransactionalCache 也是⼀种装饰类，为 Cache 增加事务功能
    // 创建⼀个新的TransactionalCache，并将真正的Cache对象存进去
    return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
  }

}
