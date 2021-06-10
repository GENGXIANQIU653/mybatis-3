package org.apache.ibatis.reflection;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.submitted.automapping.Article;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author xianqiu.geng
 * @Date 2021/6/10 下午7:47
 * @Copyright zhangmen
 */
class ParamNameResolverTest {

  @Test
  public void test() throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
    Configuration config = new Configuration();
    config.setUseActualParamName(false);
    Method method = ArticleMapper.class.getMethod("select", Integer.class, String.class, RowBounds.class, Article.class);

    ParamNameResolver resolver = new ParamNameResolver(config, method);
    Field field = resolver.getClass().getDeclaredField("names");
    field.setAccessible(true);
    // 通过反射获取 ParamNameResolver 私有成员变量 names
    Object names = field.get(resolver);

    System.out.println("names: " + names);
  }

  class ArticleMapper {
    public void select(@Param("id") Integer id, @Param("author") String author, RowBounds rb, Article article) {}
  }

}
