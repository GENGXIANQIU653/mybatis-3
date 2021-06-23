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
 *
 * 实现 Executor 接口，支持二级缓存的 Executor 的实现类
 */
public class CachingExecutor implements Executor {

  /**
   * 被委托的 Executor 对象
   */
  private final Executor delegate;

  /**
   * TransactionalCacheManager 对象，支持事务的缓存管理器。
   * 因为二级缓存是支持跨 Session 进行共享，此处需要考虑事务，那么，必然需要做到事务提交时，才将当前事务中查询时产生的缓存，同步到二级缓存中。
   * 这个功能，就通过 TransactionalCacheManager 来实现
   */
  private final TransactionalCacheManager tcm = new TransactionalCacheManager();

  public CachingExecutor(Executor delegate) {
    this.delegate = delegate;
    // <2> 设置 delegate 被当前执行器所包装
    delegate.setExecutorWrapper(this);
  }

  @Override
  public Transaction getTransaction() {
    return delegate.getTransaction();
  }

  @Override
  public void close(boolean forceRollback) {
    try {
      // issues #499, #524 and #573
      if (forceRollback) {
        tcm.rollback();
      } else {
        tcm.commit();
      }
    } finally {
      delegate.close(forceRollback);
    }
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public int update(MappedStatement ms, Object parameterObject) throws SQLException {
    flushCacheIfRequired(ms);
    return delegate.update(ms, parameterObject);
  }

  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    flushCacheIfRequired(ms);
    return delegate.queryCursor(ms, parameter, rowBounds);
  }


  /**
   * 获取 BoundSql 对象，创建 CacheKey 对象，然后再将这两个对象传给重载方法
   *
   * BoundSql 的获取过程较为复杂，我将在下一节进行分析
   * CacheKey 以及接下来即将出现的一二级缓存将会独立成文进行分析
   *
   * 此方法和 SimpleExecutor 父类 BaseExecutor 中的实现没什么区别，有区别的地方在于这个方法所调用的重载方法
   *
   * @param ms
   * @param parameterObject
   * @param rowBounds
   * @param resultHandler
   * @param <E>
   * @return
   * @throws SQLException
   *
   *
   */
  @Override
  public <E> List<E> query(MappedStatement ms,
                           Object parameterObject,
                           RowBounds rowBounds,
                           ResultHandler resultHandler) throws SQLException {

    /**
     * 获取 BoundSql
     */
    BoundSql boundSql = ms.getBoundSql(parameterObject);

    /**
     * 创建 CacheKey
     */
    CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);

    // 调用重载方法
    return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 代码涉及到了二级缓存，若二级缓存为空，或未命中，则调用被装饰类的 query 方法
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
  public <E> List<E> query(MappedStatement ms,
                           Object parameterObject,
                           RowBounds rowBounds,
                           ResultHandler resultHandler,
                           CacheKey key,
                           BoundSql boundSql) throws SQLException {

    /**
     * 从 MappedStatement 中获取 Cache，注意这里的 Cache 并非是在 CachingExecutor 中创建的
     *
     * 注意二级缓存是从 MappedStatement 中获取的，而非由 CachingExecutor 创建。
     * 由于 MappedStatement 存在于全局配置中，可以多个 CachingExecutor 获取到，这样就会出现线程安全问题
     */
    Cache cache = ms.getCache();

    // 若映射文件中未配置缓存或参照缓存，此时 cache = null
    if (cache != null) {

      // 刷新缓存
      flushCacheIfRequired(ms);

      if (ms.isUseCache() && resultHandler == null) {

        ensureNoOutParams(ms, boundSql);

        /**
         * 访问二级缓存
         * 调用 TransactionalCacheManager#getObject(Cache cache, CacheKey key) 方法，从二级缓存中，获取结果
         */
        @SuppressWarnings("unchecked")
        List<E> list = (List<E>) tcm.getObject(cache, key);

        // 缓存未命中
        if (list == null) {
          /**
           * 向一级缓存或者数据库进行查询
           */
          list = delegate.query(ms,
            parameterObject,
            rowBounds,
            null,
            key,
            boundSql);

          /**
           * 缓存查询结果
           * 调用 TransactionalCacheManager#put(Cache cache, CacheKey key, Object value) 方法，缓存结果到二级缓存中
           * tcm的put方法也不是直接操作缓存，只是在把这次的数据和key放入待提交的Map中
           */
          tcm.putObject(cache, key, list);
        }
        return list;
      }
    }

    /**
     * 调用被装饰类的 query 方法，通常是【BaseExecutor.query】
     */
    return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return delegate.flushStatements();
  }

  /**
   * delegate 和 tcm 先后提交
   *
   * 会把具体commit的职责委托给包装的Executor。
   * 主要是看下tcm.commit()，tcm最终又会调用到TransactionalCache
   * @param required
   * @throws SQLException
   */
  @Override
  public void commit(boolean required) throws SQLException {
    // 执行 delegate 对应的方法
    delegate.commit(required);
    // 提交 TransactionalCacheManager
    tcm.commit();
  }

  /**
   * delegate 和 tcm 先后回滚
   * @param required
   * @throws SQLException
   */
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

  private void flushCacheIfRequired(MappedStatement ms) {
    Cache cache = ms.getCache();
    if (cache != null && ms.isFlushCacheRequired()) {
      tcm.clear(cache);
    }
  }

  @Override
  public void setExecutorWrapper(Executor executor) {
    throw new UnsupportedOperationException("This method should not be called");
  }

}
