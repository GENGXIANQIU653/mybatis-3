/**
 *    Copyright 2009-2020 the original author or authors.
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

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class PluginTest {

  @Test
  void mapPluginShouldInterceptGet() {
    Map map = new HashMap();
    map = (Map) new AlwaysMapPlugin().plugin(map);
    assertEquals("Always", map.get("Anything"));
  }

  @Test
  void shouldNotInterceptToString() {
    Map map = new HashMap();
    map = (Map) new AlwaysMapPlugin().plugin(map);
    assertNotEquals("Always", map.toString());
  }

  /**
   *
   * 插件类
   *
   * <1> 处通过 @Intercepts 和 @Signature 注解，定义了需要拦截的方法为 Map 类型、方法为 "get" 方法，方法参数为 Object.class
   */
  @Intercepts({
      @Signature(type = Map.class,
        method = "get",
        args = {Object.class}
        )
  }) // <1>
  public static class AlwaysMapPlugin implements Interceptor {

    /**
     * 在实现方法 #intercept(Invocation invocation) 方法，直接返回 "Always" 字符串。也就是说，当所有的 target 类型为 Map 类型，并且调用 Map#get(Object) 方法时，返回的都是 "Always"
     * @param invocation 调用信息
     * @return
     */
    @Override // <4>
    public Object intercept(Invocation invocation) {
      return "Always";
    }

    /**
     * 在实现方法 #plugin(Object target) 方法内部，调用 Plugin#wrap(Object target, Interceptor interceptor) 方法，执行代理对象的创建
     * @param target 目标对象
     * @return
     */
    @Override // <2>
    public Object plugin(Object target) {
      return Plugin.wrap(target, this);
    }

    // <3>
    public void setProperties(Properties properties) {
    }

  }

}
