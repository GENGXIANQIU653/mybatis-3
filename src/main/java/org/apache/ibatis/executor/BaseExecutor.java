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

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public abstract class BaseExecutor implements Executor {

  private static final Log log = LogFactory.getLog(BaseExecutor.class);

  /**
   * 事务对象
   */
  protected Transaction transaction;

  /**
   * 包装的 Executor 对象
   */
  protected Executor wrapper;

  /**
   * DeferredLoad( 延迟加载 ) 队列
   */
  protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;

  /**
   * 本地缓存，即一级缓存
   * 一级缓存的类型为 PerpetualCache，没有被其他缓存类装饰过。
   * 一级缓存所存储从查询结果会在 MyBatis 执行更新操作（INSERT/UPDATE/DELETE），以及提交和回滚事务时被清空
   */
  protected PerpetualCache localCache;

  protected PerpetualCache localOutputParameterCache;

  protected Configuration configuration;

  /**
   * 记录嵌套查询的层级
   */
  protected int queryStack;

  /**
   * 是否关闭
   */
  private boolean closed;

  protected BaseExecutor(Configuration configuration, Transaction transaction) {
    this.transaction = transaction;
    this.deferredLoads = new ConcurrentLinkedQueue<>();
    this.localCache = new PerpetualCache("LocalCache");
    this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
    this.closed = false;
    this.configuration = configuration;
    // 自己
    this.wrapper = this;
  }

  /**
   * 获得事务对象
   * @return
   */
  @Override
  public Transaction getTransaction() {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return transaction;
  }

  @Override
  public void close(boolean forceRollback) {
    try {
      try {
        rollback(forceRollback);
      } finally {
        if (transaction != null) {
          transaction.close();
        }
      }
    } catch (SQLException e) {
      // Ignore. There's nothing that can be done at this point.
      log.warn("Unexpected exception on closing transaction.  Cause: " + e);
    } finally {
      transaction = null;
      deferredLoads = null;
      localCache = null;
      localOutputParameterCache = null;
      closed = true;
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  /**
   * 执行写操作
   * @param ms
   * @param parameter
   * @return
   * @throws SQLException
   */
  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    // <1> 已经关闭，则抛出 ExecutorException 异常
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // <2> 清空本地缓存
    clearLocalCache();
    // <3> 执行写操作
    return doUpdate(ms, parameter);
  }

  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return flushStatements(false);
  }

  public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return doFlushStatements(isRollBack);
  }

  /**
   * 读操作
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> List<E> query(MappedStatement ms,
                           Object parameter,
                           RowBounds rowBounds,
                           ResultHandler resultHandler) throws SQLException {

    // 获取 BoundSql
    BoundSql boundSql = ms.getBoundSql(parameter);

    /**
     * 创建 CacheKey
     */
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);

    // 调用重载方法
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 读操作，增加了cacheKey 和 boundSql
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param key
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  @SuppressWarnings("unchecked")
  @Override
  public <E> List<E> query(MappedStatement ms,
                           Object parameter,
                           RowBounds rowBounds,
                           ResultHandler resultHandler,
                           CacheKey key,
                           BoundSql boundSql) throws SQLException {

    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());

    // 1、已经关闭，则抛出 ExecutorException 异常
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }

    // 2、清空本地缓存，如果 queryStack 为零，并且要求清空本地缓存
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      clearLocalCache();
    }

    // 查询结果
    List<E> list;

    try {
      // 3、queryStack + 1
      queryStack++;
      /**
       * 【4.1】从一级缓存中获取缓存项
       */
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;

      /**
       * 【4.2】命中缓存
       */
      if (list != null) {
        // 存储过程相关逻辑，忽略
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      } else {
        /**
         * 【4.3 重要】一级缓存未命中，则从数据库中查询，并将查询结果写入缓存中，见detail
         */
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }

    } finally {
      // queryStack - 1
      queryStack--;
    }
    if (queryStack == 0) {
      // 从一级缓存中延迟加载嵌套查询结果
      for (DeferredLoad deferredLoad : deferredLoads) {
        // 执行延迟加载
        deferredLoad.load();
      }

      deferredLoads.clear();

      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {

        clearLocalCache();
      }
    }
    return list;
  }

  /**
   * 执行查询，返回的结果为 Cursor 游标对象
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    // <1> 获得 BoundSql 对象
    BoundSql boundSql = ms.getBoundSql(parameter);
    // 执行查询
    return doQueryCursor(ms, parameter, rowBounds, boundSql);
  }

  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
    if (deferredLoad.canLoad()) {
      deferredLoad.load();
    } else {
      deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
    }
  }

  /**
   * 创建cacheKey
   * @param ms
   * @param parameterObject
   * @param rowBounds
   * @param boundSql
   * @return
   */
  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 创建 CacheKey 对象
    CacheKey cacheKey = new CacheKey();

    // 将 MappedStatement 的 id 作为影响因子进行计算
    cacheKey.update(ms.getId());
    // RowBounds 用于分页查询，下面将它的两个字段作为影响因子进行计算
    cacheKey.update(rowBounds.getOffset());
    cacheKey.update(rowBounds.getLimit());
    // 获取 sql 语句，并进行计算
    cacheKey.update(boundSql.getSql());

    // 设置 ParameterMapping 数组的元素对应的每个 value 到 CacheKey 对象中
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

    // TypeHandlerRegistry
    TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();

    for (ParameterMapping parameterMapping : parameterMappings) {
      if (parameterMapping.getMode() != ParameterMode.OUT) {
        // 运行时参数
        Object value;
        // 当前大段代码用于获取 SQL 中的占位符 #{xxx} 对应的运行时参数，
        // 前文有类似分析，这里忽略了
        String propertyName = parameterMapping.getProperty();
        if (boundSql.hasAdditionalParameter(propertyName)) {
          value = boundSql.getAdditionalParameter(propertyName);
        } else if (parameterObject == null) {
          value = null;
        } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
          value = parameterObject;
        } else {
          MetaObject metaObject = configuration.newMetaObject(parameterObject);
          value = metaObject.getValue(propertyName);
        }
        // 让运行时参数参与计算
        cacheKey.update(value);
      }
    }
    if (configuration.getEnvironment() != null) {
      // 获取 Environment id，并让其参与计算
      cacheKey.update(configuration.getEnvironment().getId());
    }
    return cacheKey;
  }

  /**
   * 判断一级缓存是否存在
   * @param ms
   * @param key
   * @return
   */
  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return localCache.getObject(key) != null;
  }

  @Override
  public void commit(boolean required) throws SQLException {
    // 已经关闭，则抛出 ExecutorException 异常
    if (closed) {
      throw new ExecutorException("Cannot commit, transaction is already closed");
    }
    // 清空本地缓存
    clearLocalCache();
    // 刷入批处理语句
    flushStatements();
    // 是否要求提交事务。如果是，则提交事务。
    if (required) {
      transaction.commit();
    }
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    if (!closed) {
      try {
        // 清空本地缓存
        clearLocalCache();
        // 刷入批处理语句
        flushStatements(true);
      } finally {
        if (required) {
          // 是否要求回滚事务。如果是，则回滚事务
          transaction.rollback();
        }
      }
    }
  }

  /**
   * 清理一级（本地）缓存
   */
  @Override
  public void clearLocalCache() {
    if (!closed) {
      // 清理 localCache
      localCache.clear();
      // 清理 localOutputParameterCache
      localOutputParameterCache.clear();
    }
  }

  /**
   * 这是个抽象方法，由子类实现
   * @param ms
   * @param parameter
   * @return
   * @throws SQLException
   */
  protected abstract int doUpdate(MappedStatement ms, Object parameter) throws SQLException;

  /**
   * 这是个抽象方法，由子类实现。
   * @param isRollback
   * @return
   * @throws SQLException
   */
  protected abstract List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException;

  /**
   * 这是个抽象方法，由子类实现
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  protected abstract <E> List<E> doQuery(MappedStatement ms,
                                         Object parameter,
                                         RowBounds rowBounds,
                                         ResultHandler resultHandler,
                                         BoundSql boundSql) throws SQLException;

  /**
   * 这是个抽象方法，由子类实现
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms,
                                                 Object parameter,
                                                 RowBounds rowBounds,
                                                 BoundSql boundSql) throws SQLException;

  protected void closeStatement(Statement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }

  /**
   * Apply a transaction timeout.
   *
   * @param statement
   *          a current statement
   * @throws SQLException
   *           if a database access error occurs, this method is called on a closed <code>Statement</code>
   * @since 3.4.0
   * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
   */
  protected void applyTransactionTimeout(Statement statement) throws SQLException {
    StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
  }

  private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {
      final Object cachedParameter = localOutputParameterCache.getObject(key);
      if (cachedParameter != null && parameter != null) {
        final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
        final MetaObject metaParameter = configuration.newMetaObject(parameter);
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
          if (parameterMapping.getMode() != ParameterMode.IN) {
            final String parameterName = parameterMapping.getProperty();
            final Object cachedValue = metaCachedParameter.getValue(parameterName);
            metaParameter.setValue(parameterName, cachedValue);
          }
        }
      }
    }
  }

  /**
   * 从数据库查询
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param key
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  private <E> List<E> queryFromDatabase(MappedStatement ms,
                                        Object parameter,
                                        RowBounds rowBounds,
                                        ResultHandler resultHandler,
                                        CacheKey key, BoundSql boundSql) throws SQLException {

    List<E> list;

    // <1> 在缓存中，添加占位对象。此处的占位符，和延迟加载有关，可见 DeferredLoad#canLoad() 方法
    localCache.putObject(key, EXECUTION_PLACEHOLDER);

    try {
      /**
       * 2.查询数据库，调用 doQuery 进行查询，见【simpleExecutor】
       *
       * ps:这是个抽象方法，由子类实现
       */
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);

    } finally {
      // 3.移除占位符
      localCache.removeObject(key);
    }
    // 4.缓存查询结果
    localCache.putObject(key, list);

    // 存储过程相关逻辑，忽略
    if (ms.getStatementType() == StatementType.CALLABLE) {
      localOutputParameterCache.putObject(key, parameter);
    }
    return list;
  }

  protected Connection getConnection(Log statementLog) throws SQLException {
    Connection connection = transaction.getConnection();
    if (statementLog.isDebugEnabled()) {
      return ConnectionLogger.newInstance(connection, statementLog, queryStack);
    } else {
      return connection;
    }
  }

  /**
   * 设置包装器
   * @param wrapper
   */
  @Override
  public void setExecutorWrapper(Executor wrapper) {
    this.wrapper = wrapper;
  }

  private static class DeferredLoad {

    private final MetaObject resultObject;
    private final String property;
    private final Class<?> targetType;
    private final CacheKey key;
    private final PerpetualCache localCache;
    private final ObjectFactory objectFactory;
    private final ResultExtractor resultExtractor;

    // issue #781
    public DeferredLoad(MetaObject resultObject,
                        String property,
                        CacheKey key,
                        PerpetualCache localCache,
                        Configuration configuration,
                        Class<?> targetType) {
      this.resultObject = resultObject;
      this.property = property;
      this.key = key;
      this.localCache = localCache;
      this.objectFactory = configuration.getObjectFactory();
      this.resultExtractor = new ResultExtractor(configuration, objectFactory);
      this.targetType = targetType;
    }

    public boolean canLoad() {
      return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER;
    }

    public void load() {
      @SuppressWarnings("unchecked")
      // we suppose we get back a List
      List<Object> list = (List<Object>) localCache.getObject(key);
      Object value = resultExtractor.extractObjectFromList(list, targetType);
      resultObject.setValue(property, value);
    }

  }

}
