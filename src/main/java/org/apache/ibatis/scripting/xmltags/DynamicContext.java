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
package org.apache.ibatis.scripting.xmltags;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import ognl.OgnlContext;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 *
 * 动态 SQL ，用于每次执行 SQL 操作时，记录动态 SQL 处理后的最终 SQL 字符串
 */
public class DynamicContext {

  public static final String PARAMETER_OBJECT_KEY = "_parameter";
  public static final String DATABASE_ID_KEY = "_databaseId";

  static {
    // <1.2> 设置 OGNL 的属性访问器
    OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
  }

  /**
   * 上下文的参数集合
   */
  private final ContextMap bindings;

  /**
   * 生成后的 SQL
   */
  private final StringJoiner sqlBuilder = new StringJoiner(" ");

  /**
   * 唯一编号。在 ForEachHandler 中使用
   */
  private int uniqueNumber = 0;

  /**
   * 当需要使用到 OGNL 表达式时，parameterObject 非空
   * @param configuration
   * @param parameterObject
   */
  public DynamicContext(Configuration configuration, Object parameterObject) {

    // <1> 初始化 bindings 参数，即 创建上下文的参数集合 ContextMap
    if (parameterObject != null && !(parameterObject instanceof Map)) {
      // <1.1>
      MetaObject metaObject = configuration.newMetaObject(parameterObject);
      boolean existsTypeHandler = configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass());
      bindings = new ContextMap(metaObject, existsTypeHandler);
    } else {
      bindings = new ContextMap(null, false);
    }

    // <2> 添加 bindings 的默认值，即 存放运行时参数 parameterObject 以及 databaseId
    bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
    bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
  }

  public Map<String, Object> getBindings() {
    return bindings;
  }

  public void bind(String name, Object value) {
    bindings.put(name, value);
  }

  /**
   * sqlBuilder 属性相关的方法
   * @param sql
   */
  public void appendSql(String sql) {
    sqlBuilder.add(sql);
  }

  /**
   * sqlBuilder 属性相关的方法
   * @return
   */
  public String getSql() {
    return sqlBuilder.toString().trim();
  }

  /**
   * uniqueNumber 属性相关的方法
   * @return
   */
  public int getUniqueNumber() {
    return uniqueNumber++;
  }

  /**
   * ContextMap ，是 DynamicContext 的内部静态类，继承 HashMap 类，上下文的参数集合
   */
  static class ContextMap extends HashMap<String, Object> {

    private static final long serialVersionUID = 2977601501966151582L;

    /**
     * parameter 对应的 MetaObject 对象
     */
    private final MetaObject parameterMetaObject;

    private final boolean fallbackParameterObject;

    public ContextMap(MetaObject parameterMetaObject, boolean fallbackParameterObject) {
      this.parameterMetaObject = parameterMetaObject;
      this.fallbackParameterObject = fallbackParameterObject;
    }

    @Override
    public Object get(Object key) {
      String strKey = (String) key;
      // 检查是否包含 strKey，若包含则直接返回
      if (super.containsKey(strKey)) {
        return super.get(strKey);
      }

      if (parameterMetaObject == null) {
        return null;
      }

      if (fallbackParameterObject && !parameterMetaObject.hasGetter(strKey)) {
        return parameterMetaObject.getOriginalObject();
      } else {
        // 从运行时参数中查找结果
        return parameterMetaObject.getValue(strKey);
      }
    }
  }

  /**
   * ContextAccessor ，是 DynamicContext 的内部静态类，实现 ognl.PropertyAccessor 接口，上下文访问器
   */
  static class ContextAccessor implements PropertyAccessor {

    @Override
    public Object getProperty(Map context, Object target, Object name) {

      Map map = (Map) target;

      // 优先从 ContextMap 中，获得属性
      Object result = map.get(name);
      if (map.containsKey(name) || result != null) {
        return result;
      }

      // <x> 如果没有，则从 PARAMETER_OBJECT_KEY 对应的 Map 中，获得属性
      Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
      if (parameterObject instanceof Map) {
        return ((Map)parameterObject).get(name);
      }

      return null;
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value) {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}
