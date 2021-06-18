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
package org.apache.ibatis.session.defaults;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.result.DefaultMapResultHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * The default implementation for {@link SqlSession}.
 * Note that this class is not Thread-Safe.
 *
 * @author Clinton Begin
 *
 * 实现 SqlSession 接口，默认的 SqlSession 实现类
 *
 */
public class DefaultSqlSession implements SqlSession {

  private final Configuration configuration;
  private final Executor executor;

  /**
   * 是否自动提交事务
   */
  private final boolean autoCommit;

  /**
   * 是否发生数据变更
   */
  private boolean dirty;

  /**
   * Cursor 数组
   */
  private List<Cursor<?>> cursorList;

  /**
   * 构造函数
   * @param configuration
   * @param executor
   * @param autoCommit
   */
  public DefaultSqlSession(Configuration configuration, Executor executor, boolean autoCommit) {
    this.configuration = configuration;
    this.executor = executor;
    this.dirty = false;
    this.autoCommit = autoCommit;
  }

  public DefaultSqlSession(Configuration configuration, Executor executor) {
    this(configuration, executor, false);
  }

  @Override
  public <T> T selectOne(String statement) {
    return this.selectOne(statement, null);
  }

  /**
   * selectOne
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @param <T>
   * @return
   */
  @Override
  public <T> T selectOne(String statement, Object parameter) {

    // 内部调用 #selectList(String statement, Object parameter) 方法，进行实现
    List<T> list = this.selectList(statement, parameter);

    if (list.size() == 1) {
      // 返回结果
      return list.get(0);
    }

    else if (list.size() > 1) {
      // 如果查询结果大于1则抛出异常，这个异常也是很常见的
      throw new TooManyResultsException("Expected one result (or null) to be returned by selectOne(), but found: " + list.size());
    }

    else {
      return null;
    }
  }

  /**
   * selectMap - 1
   * @param statement Unique identifier matching the statement to use.
   * @param mapKey The property to use as key for each value in the list.
   * @param <K>
   * @param <V>
   * @return
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
    // 调用selectMap - 3
    return this.selectMap(statement, null, mapKey, RowBounds.DEFAULT);
  }

  /**
   * selectMap - 2
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @param mapKey The property to use as key for each value in the list.
   * @param <K>
   * @param <V>
   * @return
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
    // 调用 selectMap - 3
    return this.selectMap(statement, parameter, mapKey, RowBounds.DEFAULT);
  }

  /**
   * selectMap - 3
   * 查询结果，并基于 Map 聚合结果
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @param mapKey The property to use as key for each value in the list.
   * @param rowBounds  Bounds to limit object retrieval
   * @param <K>
   * @param <V>
   * @return
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {

    /**
     * <1> 执行查询
     */
    final List<? extends V> list = selectList(statement, parameter, rowBounds);

    /**
     * <2> 创建 DefaultMapResultHandler 对象
     */
    final DefaultMapResultHandler<K, V> mapResultHandler = new DefaultMapResultHandler<>(mapKey,
            configuration.getObjectFactory(), configuration.getObjectWrapperFactory(), configuration.getReflectorFactory());

    /**
     * <3> 创建 DefaultResultContext 对象
     */
    final DefaultResultContext<V> context = new DefaultResultContext<>();

    /**
     *  <4> 遍历查询结果
     */
    for (V o : list) {
      // 设置 DefaultResultContext 中
      context.nextResultObject(o);
      /**
       * 使用 DefaultMapResultHandler 处理结果的当前元素
       */
      mapResultHandler.handleResult(context);
    }
    // <5> 返回结果
    return mapResultHandler.getMappedResults();
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement) {
    return selectCursor(statement, null);
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter) {
    return selectCursor(statement, parameter, RowBounds.DEFAULT);
  }

  /**
   *
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @param rowBounds  Bounds to limit object retrieval
   * @param <T>
   * @return
   */
  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds) {
    try {
      // <1> 获得 MappedStatement 对象
      MappedStatement ms = configuration.getMappedStatement(statement);

      // <2> 执行查询
      Cursor<T> cursor = executor.queryCursor(ms, wrapCollection(parameter), rowBounds);

      // <3> 添加 cursor 到 cursorList 中
      registerCursor(cursor);

      return cursor;

    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  /**
   * selectList - 1
   * @param statement Unique identifier matching the statement to use.
   * @param <E>
   * @return
   */
  @Override
  public <E> List<E> selectList(String statement) {

    // 调用 selectList - 2
    return this.selectList(statement, null);
  }

  /**
   * selectList - 2
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @param <E>
   * @return
   */
  @Override
  public <E> List<E> selectList(String statement, Object parameter) {

    // 调用 selectList - 3
    return this.selectList(statement, parameter, RowBounds.DEFAULT);
  }

  /**
   * selectList - 3
   * @param statement Unique identifier matching the statement to use.
   * @param parameter A parameter object to pass to the statement.
   * @param rowBounds  Bounds to limit object retrieval
   * @param <E>
   * @return
   */
  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {

    // 调用 selectList - 4
    return selectList(statement, parameter, rowBounds, Executor.NO_RESULT_HANDLER);
  }

  /**
   * selectList - 4
   * @param statement
   * @param parameter
   * @param rowBounds
   * @param handler
   * @param <E>
   * @return
   */
  private <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    try {
      /**
       * <1> 获得 MappedStatement 对象
       */
      MappedStatement ms = configuration.getMappedStatement(statement);

      /**
       * <2> 调用 Executor 实现类中的 query 方法，执行查询
       *
       * 这里要来说说 executor 变量，该变量类型为 Executor。Executor 是一个接口，它的实现类包括【CachingExecutor】、BaseExecutor
       * 【SimpleExecutor、ReuseExecutor、BatchExecutor、ClosedExecutor】
       *
       * 默认情况下，executor 的类型为 【CachingExecutor】，该类是一个装饰器类，用于给目标 Executor 增加二级缓存功能。
       * 那目标 Executor 是谁呢？默认情况下是 【SimpleExecutor】
       */
      return executor.query(ms, wrapCollection(parameter), rowBounds, handler);

    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public void select(String statement, Object parameter, ResultHandler handler) {
    select(statement, parameter, RowBounds.DEFAULT, handler);
  }

  @Override
  public void select(String statement, ResultHandler handler) {
    select(statement, null, RowBounds.DEFAULT, handler);
  }

  /**
   * select
   * @param statement
   *          Unique identifier matching the statement to use.
   * @param parameter
   *          the parameter
   * @param rowBounds
   *          RowBound instance to limit the query results
   * @param handler
   */
  @Override
  public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    selectList(statement, parameter, rowBounds, handler);
  }

  @Override
  public int insert(String statement) {
    return insert(statement, null);
  }

  @Override
  public int insert(String statement, Object parameter) {
    // 基于 #update(...) 方法来实现
    return update(statement, parameter);
  }

  @Override
  public int update(String statement) {
    return update(statement, null);
  }

  @Override
  public int update(String statement, Object parameter) {
    try {
      // <1> 标记 dirty ，表示执行过写操作，该参数，会在事务的提交和回滚，产生其用途
      dirty = true;

      // <2> 获得 MappedStatement 对象
      MappedStatement ms = configuration.getMappedStatement(statement);

      // <3> 执行更新操作
      return executor.update(ms, wrapCollection(parameter));

    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error updating database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public int delete(String statement) {
    return update(statement, null);
  }

  @Override
  public int delete(String statement, Object parameter) {

    // 基于 #update(...) 方法来实现
    return update(statement, parameter);
  }

  @Override
  public void commit() {
    commit(false);
  }

  @Override
  public void commit(boolean force) {
    try {
      // 提交事务,isCommitOrRollbackRequired
      executor.commit(isCommitOrRollbackRequired(force));
      // 标记 dirty 为 false
      dirty = false;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error committing transaction.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public void rollback() {
    rollback(false);
  }

  @Override
  public void rollback(boolean force) {
    try {
      // 回滚事务
      executor.rollback(isCommitOrRollbackRequired(force));
      // 标记 dirty 为 false
      dirty = false;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error rolling back transaction.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  /**
   * 提交批处理
   * @return
   */
  @Override
  public List<BatchResult> flushStatements() {
    try {
      return executor.flushStatements();
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error flushing statements.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  /**
   * 关闭会话
   */
  @Override
  public void close() {
    try {
      // <1> 关闭执行器
      executor.close(isCommitOrRollbackRequired(false));
      // <2> 关闭所有游标
      closeCursors();
      // <3> 重置 dirty 为 false
      dirty = false;
    } finally {
      ErrorContext.instance().reset();
    }
  }

  private void closeCursors() {
    if (cursorList != null && !cursorList.isEmpty()) {
      for (Cursor<?> cursor : cursorList) {
        try {
          cursor.close();
        } catch (IOException e) {
          throw ExceptionFactory.wrapException("Error closing cursor.  Cause: " + e, e);
        }
      }
      cursorList.clear();
    }
  }

  @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  /**
   *
   * @param type Mapper interface class
   * @param <T>
   * @return
   */
  @Override
  public <T> T getMapper(Class<T> type) {

    /**
     * Configuration.getMapper, 见detail
     */
    return configuration.getMapper(type, this);
  }

  @Override
  public Connection getConnection() {
    try {
      return executor.getTransaction().getConnection();
    } catch (SQLException e) {
      throw ExceptionFactory.wrapException("Error getting a new connection.  Cause: " + e, e);
    }
  }

  @Override
  public void clearCache() {
    executor.clearLocalCache();
  }

  private <T> void registerCursor(Cursor<T> cursor) {
    if (cursorList == null) {
      cursorList = new ArrayList<>();
    }
    cursorList.add(cursor);
  }

  /**
   * 判断是否执行提交或回滚
   * @param force
   * @return
   *
   * 有两种情况需要触发：
   * 1）未开启自动提交，并且数据发生写操作
   * 2）强制提交
   *
   */
  private boolean isCommitOrRollbackRequired(boolean force) {
    return (!autoCommit && dirty) || force;
  }

  private Object wrapCollection(final Object object) {
    return ParamNameResolver.wrapToMapIfCollection(object, null);
  }

  /**
   * @deprecated Since 3.5.5
   */
  @Deprecated
  public static class StrictMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -5741767162221585340L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + this.keySet());
      }
      return super.get(key);
    }

  }

}
