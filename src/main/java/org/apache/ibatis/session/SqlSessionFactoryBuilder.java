/**
 *    Copyright 2009-2021 the original author or authors.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

/**
 * Builds {@link SqlSession} instances.
 *
 * @author Clinton Begin
 *
 * SqlSessionFactory 构造器
 * 提供了各种 build 的重载方法，核心的套路都是解析出 Configuration 配置对象，从而创建出 DefaultSqlSessionFactory 对象
 *
 *
 * // 构建 SqlSessionFactory 对象
 * Reader reader = Resources.getResourceAsReader("org/apache/ibatis/autoconstructor/mybatis-config.xml");
 * SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
 *
 * // 获得 SqlSession 对象
 * SqlSession sqlSession = sqlSessionFactory.openSession();
 *
 * // 获得 Mapper 对象
 * final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
 *
 * // 执行查询
 * final Object subject = mapper.getSubject(1);
 *
 */
public class SqlSessionFactoryBuilder {

  /**
   * 入口
   * @param reader
   * @return
   */
  public SqlSessionFactory build(Reader reader) {
    return build(reader, null, null);
  }

  public SqlSessionFactory build(Reader reader, String environment) {
    return build(reader, environment, null);
  }

  public SqlSessionFactory build(Reader reader, Properties properties) {
    return build(reader, null, properties);
  }

  /**
   * 入口2 - 构造 SqlSessionFactory 对象
   * @param reader Reader 对象
   * @param environment 环境
   * @param properties Properties 变量
   * @return SqlSessionFactory 对象
   */
  public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
    try {

      /**
       * XPathParser
       * <1> 创建 XMLConfigBuilder 对象
       */
      XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);

      /**
       * <2> parser.parse() -> 执行 XML 解析,调用 XMLConfigBuilder#parse() 方法，执行 XML 解析，返回 Configuration 对象
       * <3> build -> 创建 DefaultSqlSessionFactory 对象
       */
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        reader.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  public SqlSessionFactory build(InputStream inputStream) {
    return build(inputStream, null, null);
  }

  public SqlSessionFactory build(InputStream inputStream, String environment) {
    return build(inputStream, environment, null);
  }

  public SqlSessionFactory build(InputStream inputStream, Properties properties) {
    return build(inputStream, null, properties);
  }

  /**
   * 构造 SqlSessionFactory 对象
   * @param inputStream
   * @param environment
   * @param properties
   * @return
   */
  public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {

    try {
      // 1. 创建 XMLConfigBuilder 对象
      XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);

      // 2. 执行 XML 解析，返回 Configuration 对象
      // 3. 创建 DefaultSqlSessionFactory 对象
      return build(parser.parse());

    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        inputStream.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  /**
   * 创建 DefaultSqlSessionFactory 对象
   *
   * @param config Configuration 对象
   * @return DefaultSqlSessionFactory 对象
   */
  public SqlSessionFactory build(Configuration config) {
    return new DefaultSqlSessionFactory(config);
  }

}
