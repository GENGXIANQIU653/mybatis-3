package org.apache.ibatis.plugin;

/**
 * @author xianqiu.geng
 * @Date 2021/6/24 下午2:27
 * @Copyright zhangmen
 */
public class Student {
  private Integer id;
  private String name;
  private Integer age;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getAge() {
    return age;
  }

  public void setAge(Integer age) {
    this.age = age;
  }
}
