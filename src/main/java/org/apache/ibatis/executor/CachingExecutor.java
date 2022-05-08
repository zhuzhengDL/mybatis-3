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
package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class CachingExecutor implements Executor {

  /**
   * 被委托的 Executor 对象  BatchExecutor/ReuseExecutor/SimpleExecutor 三个中间的一个
   */
  private final Executor delegate;
  /**
   * TransactionalCacheManager 对象
   */
  private final TransactionalCacheManager tcm = new TransactionalCacheManager();

  public CachingExecutor(Executor delegate) {
    // <1>
    this.delegate = delegate;
    // <2> 设置 delegate 被当前执行器所包装
    delegate.setExecutorWrapper(this);
  }

  @Override
  public Transaction getTransaction() {
    return delegate.getTransaction();
  }


  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }
  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return delegate.flushStatements();
  }
  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return delegate.isCached(ms, key);
  }

  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    delegate.deferLoad(ms, resultObject, property, key, targetType);
  }

  @Override
  public void clearLocalCache() {
    delegate.clearLocalCache();
  }

  @Override
  public int update(MappedStatement ms, Object parameterObject) throws SQLException {
    // 如果需要清空缓存，则进行清空
    flushCacheIfRequired(ms);
    // 执行 delegate 对应的方法
    return delegate.update(ms, parameterObject);
  }

  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    // 如果需要清空缓存，则进行清空
    flushCacheIfRequired(ms);
    // 执行 delegate 对应的方法
    return delegate.queryCursor(ms, parameter, rowBounds);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    // 获得 BoundSql 对象
    BoundSql boundSql = ms.getBoundSql(parameterObject);
    // 创建 CacheKey 对象
    CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
    // 查询
    return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 注意⼆级缓存是从 MappedStatement 中获取的。由于 MappedStatement 存在于全局配置
   * 中，可以多个 CachingExecutor 获取到，这样就会出现线程安全问题。除此之外，若不加以控制，多个
   * 事务共⽤⼀个缓存实例，会导致脏读问题。⾄于脏读问题，需要借助其他类来处理，也就是上⾯代码中
   * tcm 变量对应的类型。下
   * @see org.apache.ibatis.cache.TransactionalCacheManager
   * @param ms
   * @param parameterObject
   * @param rowBounds
   * @param resultHandler
   * @param key
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
      throws SQLException {
    // <1>// 从 MappedStatement 中获取 Cache，注意这⾥的 Cache 是从MappedStatement中获取的
    // // 也就是我们上⾯解析Mapper中<cache/>标签中创建的，它保存在Configration中
    // // 我们在上⾯解析blog.xml时分析过每⼀个MappedStatement都有⼀个Cache对象，就是这⾥
    Cache cache = ms.getCache();
    if (cache != null) {// <2>
      // <2.1> 如果需要清空缓存，则进行清空 flushCache="true"
      flushCacheIfRequired(ms);
      if (ms.isUseCache() && resultHandler == null) { // <2.2>
        // 暂时忽略，存储过程相关
        ensureNoOutParams(ms, boundSql);
        @SuppressWarnings("unchecked")
        // <2.3> 从二级缓存中，获取结果
        List<E> list = (List<E>) tcm.getObject(cache, key);
        if (list == null) {
          // <2.4.1> 如果不存在，则从数据库中查询
          // 如果没有值，则执⾏查询，这个查询实际也是先⾛⼀级缓存查询，⼀级缓存也没 有的话，则进⾏DB查询
          list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
          // <2.4.2> 缓存结果到二级缓存中
          tcm.putObject(cache, key, list); // issue #578 and #116
        }
        // <2.5> 如果存在，则直接返回结果
        return list;
      }
    }
    // <3> 不使用缓存，则从数据库中查询
    return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }



  @Override
  public void commit(boolean required) throws SQLException {
    // 执行 delegate 对应的方法
    delegate.commit(required);
    // 提交 TransactionalCacheManager  刷新缓存数据（缓存临时数据更新到真实的cache中）
    tcm.commit();
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    try {
      // 执行 delegate 对应的方法
      delegate.rollback(required);
    } finally {
      if (required) {
        // 回滚 TransactionalCacheManager
        tcm.rollback();
      }
    }
  }

  private void ensureNoOutParams(MappedStatement ms, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {
      for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
        if (parameterMapping.getMode() != ParameterMode.IN) {
          throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
        }
      }
    }
  }


  /**
   * 如果需要清空缓存，则进行清空
   * @param ms
   */
  private void flushCacheIfRequired(MappedStatement ms) {
    ////获取MappedStatement对应的Cache，进⾏清空
    Cache cache = ms.getCache();
    //SQL需设置flushCache="true" 才会执⾏清空 非SElECT默认TRUE
    //@see org.apache.ibatis.builder.xml.XMLStatementBuilder.parseStatementNode
    if (cache != null && ms.isFlushCacheRequired()) { // 是否需要清空缓存
      tcm.clear(cache);
    }
  }

  @Override
  public void close(boolean forceRollback) {
    try {
      // issues #499, #524 and #573
      if (forceRollback) {
        // 如果强制回滚，则回滚 TransactionalCacheManager
        tcm.rollback();
      } else {
        // 否则提交 TransactionalCacheManager 刷新缓存数据（缓存临时数据更新到真实的cache中）
        tcm.commit();
      }
    } finally {
      // 执行 delegate 对应的方法
      delegate.close(forceRollback);
    }
  }
  @Override
  public void setExecutorWrapper(Executor executor) {
    throw new UnsupportedOperationException("This method should not be called");
  }

}
