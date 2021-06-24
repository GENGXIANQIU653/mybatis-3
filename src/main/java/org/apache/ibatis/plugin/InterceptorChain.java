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
package org.apache.ibatis.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Clinton Begin
 * 拦截器 Interceptor 链
 */
public class InterceptorChain {

  /**
   * 拦截器数组
   */
  private final List<Interceptor> interceptors = new ArrayList<>();

  /**
   * 应用所有拦截器到指定目标对象
   * 该方法被4处调用，所以一共可以有四种目标对象类型可以被拦截：1）Executor；2）StatementHandler；3）ParameterHandler；4）ResultSetHandler
   *
   * 我们在编写插件时，除了需要让插件类实现 Interceptor 接口，还需要通过注解标注该插件的拦截点。
   * 所谓拦截点指的是插件所能拦截的方法，MyBatis 所允许拦截的方法如下：
   * 1、Executor (update, query, flushStatements, commit, rollback, getTransaction, close, isClosed)
   * 2、ParameterHandler (getParameterObject, setParameters)
   * 3、ResultSetHandler (handleResultSets, handleOutputParameters)
   * 4、StatementHandler (prepare, parameterize, batch, update, query)
   *
   * @param target 指定对象
   * @return
   */
  public Object pluginAll(Object target) {
    /**
     * 遍历拦截器集合
     * add by gxq 2021-06-24
     * 此拦截器集合是在解析"mybatis-config.xml"文件时，读取的<plugins></plugins>配置
     */
    for (Interceptor interceptor : interceptors) {
      // 调用拦截器的 plugin 方法植入相应的插件逻辑
      target = interceptor.plugin(target);
    }
    return target;
  }

  /**
   * 添加拦截器
   * 该方法在 Configuration 的 #pluginElement(XNode parent) 方法中被调用
   * @param interceptor
   */
  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }

  public List<Interceptor> getInterceptors() {
    return Collections.unmodifiableList(interceptors);
  }

}
