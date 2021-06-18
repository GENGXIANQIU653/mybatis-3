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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 *
 * 继承 BaseBuilder 抽象类，SqlSource 构建器，负责将 SQL 语句中的 #{} 替换成相应的 ? 占位符，
 * 并获取该 ? 占位符对应的 org.apache.ibatis.mapping.ParameterMapping 对象
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  /**
   * 执行解析原始 SQL ，成为 SqlSource 对象
   *
   * @param originalSql 原始 SQL
   * @param parameterType 参数类型
   * @param additionalParameters 附加参数集合。可能是空集合，也可能是 bindings 集合
   * @return SqlSource 对象
   */
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {

    // <1> 创建 ParameterMappingTokenHandler 对象，即创建 #{} 占位符处理器
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);

    // <2> 创建 GenericTokenParser 对象，即创建 #{} 占位符解析器。注意，传入的参数是 #{ 和 } 对
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);

    // <3> 解析 #{} 占位符，并返回解析结果
    String sql;
    if (configuration.isShrinkWhitespacesInSql()) {
      sql = parser.parse(removeExtraWhitespaces(originalSql));
    } else {
      sql = parser.parse(originalSql);
    }
    // <4> 创建 StaticSqlSource 对象，即封装解析结果到 StaticSqlSource 中，并返回
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }

  public static String removeExtraWhitespaces(String original) {
    StringTokenizer tokenizer = new StringTokenizer(original);
    StringBuilder builder = new StringBuilder();
    boolean hasMoreTokens = tokenizer.hasMoreTokens();
    while (hasMoreTokens) {
      builder.append(tokenizer.nextToken());
      hasMoreTokens = tokenizer.hasMoreTokens();
      if (hasMoreTokens) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  /**
   *
   * ParameterMappingTokenHandler 是 SqlSourceBuilder 的内部私有静态类
   *
   * 实现 TokenHandler 接口，继承 BaseBuilder 抽象类，负责将匹配到的 #{ 和 } 对，
   * 替换成相应的 ? 占位符，并获取该 ? 占位符对应的 org.apache.ibatis.mapping.ParameterMapping 对象
   */
  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

    /**
     * ParameterMapping 数组
     */
    private List<ParameterMapping> parameterMappings = new ArrayList<>();

    /**
     * 参数类型
     */
    private Class<?> parameterType;

    /**
     * additionalParameters 参数的对应的 MetaObject 对象
     */
    private MetaObject metaParameters;

    /**
     * 构造方法
     * @param configuration
     * @param parameterType
     * @param additionalParameters
     */
    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      // 创建 additionalParameters 参数的对应的 MetaObject 对象
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    /**
     * 如下两个步骤，就是 ParameterMappingTokenHandler 的核心
     *
     * handleToken 方法看起来比较简单，但实际上并非如此
     * 1、GenericTokenParser 负责将 #{} 占位符中的内容抽取出来，并将抽取出的内容传给 handleToken 方法
     * 2、handleToken 方法负责将传入的参数解析成对应的 ParameterMapping 对象，这步操作由 buildParameterMapping 方法完成
     *
     * @param content Token 字符串，即openToken 和 closeToken 中间部分
     * @return
     */
    @Override
    public String handleToken(String content) {
      /**
       * <1> 构建 ParameterMapping 对象，并添加到 parameterMappings 中，见detail
       *
       * 例如
       * 此处 将"age,javaType=int,jdbcType=NUMERIC" 解析并构建成 ParameterMapping 对象
       */
      parameterMappings.add(buildParameterMapping(content));
      // <2> 返回 ? 占位符
      return "?";
    }

    /**
     * 构建 ParameterMapping 对象
     * @param content
     * @return
     *
     * 代码很多，逻辑看起来很复杂。但是它做的事情却不是很多，只有3件事情。如下：
     *
     * 1、解析 content
     * 2、解析 propertyType，对应分割线之上的代码
     * 3、构建 ParameterMapping 对象，对应分割线之下的代码
     */
    private ParameterMapping buildParameterMapping(String content) {

      /**
       * 将 #{xxx} 占位符中的内容解析成 Map。大家可能很好奇一个普通的字符串是怎么解析成 Map 的，
       * 举例说明一下。如下：
       *
       *    #{age,javaType=int,jdbcType=NUMERIC,typeHandler=MyTypeHandler}
       *
       * 上面占位符中的内容最终会被解析成如下的结果：
       *
       *  {
       *      "property": "age",
       *      "typeHandler": "MyTypeHandler",
       *      "jdbcType": "NUMERIC",
       *      "javaType": "int"
       *  }
       *
       * parseParameterMapping 内部依赖 ParameterExpression 对字符串进行解析，ParameterExpression 的
       * 逻辑不是很复杂，这里就不分析了。大家若有兴趣，可自行分析
       */

      // <1> 解析成 Map 集合
      Map<String, String> propertiesMap = parseParameterMapping(content);

      // <2> 获得属性的名字和类型
      // 名字
      String property = propertiesMap.get("property");

      // 类型
      Class<?> propertyType;

      // metaParameters 为 DynamicContext 成员变量 bindings 的元信息对象
      if (metaParameters.hasGetter(property)) {
        propertyType = metaParameters.getGetterType(property);

        /**
         * parameterType 是运行时参数的类型。
         * 1、如果用户传入的是单个参数，比如 Article 对象，此时parameterType 为 Article.class。
         * 2、如果用户传入的多个参数，比如 [id = 1, author = "coolblog"]，MyBatis 会使用 ParamMap 封装这些参数，此时 parameterType 为 ParamMap.class。
         * 3、如果 parameterType 有相应的 TypeHandler，这里则把 parameterType 设为 propertyType
         */
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) {
        // 如果 property 为空，或 parameterType 是 Map 类型，则将 propertyType 设为 Object.class
        propertyType = Object.class;
      } else {

        /*
         * 代码逻辑走到此分支中，表明 parameterType 是一个自定义的类，
         * 比如 Article，此时为该类创建一个元信息对象
         */
        MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());

        // 检测参数对象有没有与 property 想对应的 getter 方法
        if (metaClass.hasGetter(property)) {

          // 获取成员变量的类型
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      }

      // --------------------propertyType 解析完成--------------------

      // -------------------------- 分割线 ---------------------------

      // <3> 创建 ParameterMapping.Builder 对象
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);

      // <3.1> 将 propertyType 赋值给 javaType，即初始化 ParameterMapping.Builder 对象的属性
      Class<?> javaType = propertyType;

      String typeHandlerAlias = null;

      // 遍历 propertiesMap
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {

        String name = entry.getKey();
        String value = entry.getValue();

        if ("javaType".equals(name)) {

          // 如果用户明确配置了 javaType，则以用户的配置为准
          javaType = resolveClass(value);
          builder.javaType(javaType);
        }

        else if ("jdbcType".equals(name)) {
          // 解析 jdbcType
          builder.jdbcType(resolveJdbcType(value));
        }

        else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }

      // <3.2> 如果 typeHandlerAlias 非空，则获得对应的 TypeHandler 对象，并设置到 ParameterMapping.Builder 对象中
      if (typeHandlerAlias != null) {
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }

      // <3.3> 创建 ParameterMapping 对象
      return builder.build();
    }

    /**
     * parseParameterMapping
     * @param content
     * @return
     */
    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
