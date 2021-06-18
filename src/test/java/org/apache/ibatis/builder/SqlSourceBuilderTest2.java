package org.apache.ibatis.builder;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

/**
 * @author xianqiu.geng
 * @Date 2021/6/18 上午11:07
 * @Copyright zhangmen
 */
public class SqlSourceBuilderTest2 {

  public class Author {
    private Integer id;
    private String name;
    private Integer age;

    // 省略 getter/setter
  }

  @Test
  public void test() {

    // 带有复杂 #{} 占位符的参数，接下里会解析这个占位符
    String sql = "SELECT * FROM Author WHERE age = #{age,javaType=int,jdbcType=NUMERIC}";
    SqlSourceBuilder sqlSourceBuilder = new SqlSourceBuilder(new Configuration());

    // 执行解析原始 SQL ，成为 SqlSource 对象
    SqlSource sqlSource = sqlSourceBuilder.parse(sql, Author.class, new HashMap<>());

    BoundSql boundSql = sqlSource.getBoundSql(new Author());

    System.out.println(String.format("SQL: %s\n", boundSql.getSql()));
    System.out.println(String.format("ParameterMappings: %s", boundSql.getParameterMappings()));
  }

}
