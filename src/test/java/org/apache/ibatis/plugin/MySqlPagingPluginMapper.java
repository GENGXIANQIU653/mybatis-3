package org.apache.ibatis.plugin;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.RowBounds;

import java.util.List;

/**
 * @author xianqiu.geng
 * @Date 2021/6/24 下午2:11
 * @Copyright zhangmen
 */
public interface MySqlPagingPluginMapper {

    List<Student> findByPaging(@Param("id") Integer id, RowBounds rb);

  List<Student> findByPaging(@Param("id") Integer id);

}
