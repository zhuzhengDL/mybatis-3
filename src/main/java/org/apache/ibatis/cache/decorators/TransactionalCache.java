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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);
  /**
   * 委托的 Cache 对象。
   *
   * 实际上，就是二级缓存 Cache 对象。
   */
  private final Cache delegate;
  /**
   * 提交时，清空 {@link #delegate}
   *
   * 初始时，该值为 false
   * 清理后{@link #clear()} 时，该值为 true ，表示持续处于清空状态
   */
  private boolean clearOnCommit;
  /**
   * 待提交的 KV 映射
   *  // 在事务被提交前，所有从数据库中查询的结果将缓存在此集合中
   */
  private final Map<Object, Object> entriesToAddOnCommit;
  /**
   * 查找不到的 KEY 集合
   * // 在事务被提交前，当缓存未命中时，CacheKey 将会被存储在此集合中
   */
  private final Set<Object> entriesMissedInCache;

  /**
   * @see TransactionalCacheManager#getTransactionalCache(org.apache.ibatis.cache.Cache)
   * @param delegate
   */
  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 存储⼆级缓存对象的时候是放到了TransactionalCache.entriesToAddOnCommit这个map中，但是每
   * 次查询的时候是直接从TransactionalCache.delegate中去查询的，所以这个⼆级缓存查询数据库后，设
   * 置缓存值是没有⽴刻⽣效的，主要是因为直接存到 delegate 会导致脏数据问题
   * @param key
   *          The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // issue #116
    // <1> 从 delegate 中获取 key 对应的 value
    // 查询的时候是直接从delegate中去查询的，也就是从真正的缓存对象中查询
    Object object = delegate.getObject(key);
    if (object == null) {
      // <2> 如果不存在，则添加到 entriesMissedInCache 中
      // 缓存未命中，则将 key 存⼊到 entriesMissedInCache 中
      entriesMissedInCache.add(key);
    }
    // issue #146
    // <3> 如果 clearOnCommit 为 true ，表示处于持续清空状态，则返回 null
    if (clearOnCommit) {
      return null;
    } else {
      // <4> 返回 value
      return object;
    }
  }

  @Override
  public void putObject(Object key, Object object) {
    // 暂存 KV 到 entriesToAddOnCommit 中
    // 将键值对存⼊到 entriesToAddOnCommit 这个Map中中，⽽⾮真实的缓存对象delegate 中
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    // <1> 标记 clearOnCommit 为 true
    clearOnCommit = true;
    // <2> 清空 entriesToAddOnCommit
    // 清空 entriesToAddOnCommit，但不清空 delegate 缓存
    entriesToAddOnCommit.clear();
  }

  public void commit() {
    // <1> 如果 clearOnCommit 为 true ，则清空 delegate 缓存
    if (clearOnCommit) {
      delegate.clear();
    }
    // 将 entriesToAddOnCommit、entriesMissedInCache 刷入 delegate 中
    // 只有session.commit()之后才会真正提交到缓存，考虑到线程安全问题，别的线程也会访问同一个namespace下的缓存
    // 刷新未缓存的结果到 delegate 缓存中
    flushPendingEntries();
    // 重置
    reset();
  }

  public void rollback() {
    // <1> 从 delegate 移除出 entriesMissedInCache
    unlockMissedEntries();
    // <2> 重置
    reset();
  }

  private void reset() {
    // 重置 clearOnCommit 为 false
    clearOnCommit = false;
    // 清空 entriesToAddOnCommit、entriesMissedInCache
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      // 将 entriesToAddOnCommit 刷入 delegate 中
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
      // 将 entriesMissedInCache 刷入 delegate 中
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifying a rollback to the cache adapter. "
            + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
