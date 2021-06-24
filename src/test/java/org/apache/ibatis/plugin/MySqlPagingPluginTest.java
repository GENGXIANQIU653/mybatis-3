package org.apache.ibatis.plugin;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Properties;

/**
 * @author xianqiu.geng
 * @Date 2021/6/24 下午1:51
 * @Copyright zhangmen
 */
class MySqlPagingPluginTest {

  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void prepare() throws Exception {
    String resource = "org/apache/ibatis/plugin/mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    inputStream.close();

    // 初始化数据到内存数据库，基于 CreateDB.sql SQL 文件
    BaseDataTest.runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
      "org/apache/ibatis/plugin/CreateDB.sql");
  }

  @Test
  public void testPlugin() {
    // 植入插件的入口，add by gxq 2021-06-24
    SqlSession session = sqlSessionFactory.openSession();
    try {
      MySqlPagingPluginMapper mapper = session.getMapper(MySqlPagingPluginMapper.class);
      List<Student> queryResult = mapper.findByPaging(1, new RowBounds(1, 3));
      // List<Student> queryResult = mapper.findByPaging(2);
      System.out.println(queryResult.size());
    } finally {
      session.close();
    }
  }

}
