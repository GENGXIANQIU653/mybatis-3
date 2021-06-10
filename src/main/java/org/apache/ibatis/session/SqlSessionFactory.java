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
package org.apache.ibatis.session;

import java.sql.Connection;

/**
 * Creates an {@link SqlSession} out of a connection or a DataSource
 *
 * @author Clinton Begin
 *
 * SqlSession 工厂接口
 *
 * 定义了 #openSession(...) 和 #getConfiguration() 两类方法
 */
public interface SqlSessionFactory {

  /**
   * openSession
   * @return
   */
  SqlSession openSession();

  /**
   * openSession
   * @param autoCommit
   * @return
   */
  SqlSession openSession(boolean autoCommit);

  /**
   * openSession
   * @param connection
   * @return
   */
  SqlSession openSession(Connection connection);

  /**
   * openSession
   * @param level
   * @return
   */
  SqlSession openSession(TransactionIsolationLevel level);

  /**
   * openSession
   * @param execType
   * @return
   */
  SqlSession openSession(ExecutorType execType);

  /**
   * openSession
   * @param execType
   * @param autoCommit
   * @return
   */
  SqlSession openSession(ExecutorType execType, boolean autoCommit);

  /**
   * openSession
   * @param execType
   * @param level
   * @return
   */
  SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level);

  /**
   * openSession
   * @param execType
   * @param connection
   * @return
   */
  SqlSession openSession(ExecutorType execType, Connection connection);

  /**
   * getConfiguration
   * @return
   */
  Configuration getConfiguration();

}
