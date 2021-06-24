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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * @author Clinton Begin
 * 实现 InvocationHandler 接口，插件类，一方面提供创建动态代理对象的方法，另一方面实现对指定类的指定方法的拦截处理。
 * 注意，Plugin 是 MyBatis 插件体系的核心类。
 *
 * Plugin 类实现了 InvocationHandler 接口，因此它可以作为参数传给 Proxy 的 newProxyInstance 方法。
 */
public class Plugin implements InvocationHandler {

  /**
   * 目标对象
   */
  private final Object target;

  /**
   * 拦截器
   */
  private final Interceptor interceptor;

  /**
   * 拦截的方法映射
   *
   * KEY：类
   * VALUE：方法集合
   */
  private final Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  /**
   * 静态方法，创建目标类的代理对象
   * @param target
   * @param interceptor
   * @return
   */
  public static Object wrap(Object target, Interceptor interceptor) {
    /**
     * <1> 获得拦截的方法映射，见detail
     *
     * 获取插件类 @Signature 注解内容，并生成相应的映射结构。形如下面：
     * {
     *     Executor.class : [query, update, commit],
     *     ParameterHandler.class : [getParameterObject, setParameters]
     * }
     *
     * add by gxq 2021-06-24
     * 严格根据 @Signature 注解内容，获取方法
     */
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    // <2> 获得目标类的类型
    // 根据 MysqlPagingPluginTest 调试，此处是CachingExecutor
    Class<?> type = target.getClass();
    /**
     * <2> 获得目标类的接口集合，见detail
     * 目标类的接口集合 必须与@Signature 注解的方法完全一致
     */
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    /**
     * <3.1> 若有接口，则创建目标对象的 JDK Proxy 对象
     */
    if (interfaces.length > 0) {
      /**
       * newProxyInstance，方法有三个参数：
       *
       * 1、loader: 用哪个类加载器去加载代理对象
       * 2、interfaces:动态代理类需要实现的接口
       * 3、h:动态代理方法在执行时，会调用h里面的invoke方法去执行
       */
      return Proxy.newProxyInstance(
          type.getClassLoader(),
          interfaces,
          // 因为 Plugin 实现了 InvocationHandler 接口，所以可以作为 JDK 动态代理的调用处理器
          new Plugin(target, interceptor, signatureMap));
    }
    // <3.2> 如果没有，则返回原始的目标对象
    return target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {

      /**
       * 获取被拦截方法列表，比如：
       *    signatureMap.get(Executor.class)，可能返回 [query, update, commit]
       */
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      // 检测方法列表是否包含被拦截的方法
      if (methods != null && methods.contains(method)) {
        // 执行插件逻辑
        return interceptor.intercept(new Invocation(target, method, args));
      }
      // 执行被拦截的方法
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  /**
   * 获得拦截的方法映射
   * 基于 @Intercepts 和 @Signature 注解
   * @param interceptor
   * @return
   */
  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    // 获取 Intercepts 注解
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);

    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
    }
    Signature[] signatures = interceptsAnnotation.value();
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
    for (Signature sig : signatures) {
      Set<Method> methods = signatureMap.computeIfAbsent(sig.type(), k -> new HashSet<>());
      try {
        Method method = sig.type().getMethod(sig.method(), sig.args());
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }

  /**
   * 获得目标类的接口集合
   * @param type
   * @param signatureMap
   * @return
   */
  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    // 接口的集合
    Set<Class<?>> interfaces = new HashSet<>();
    // 循环递归 type 类，机器父类
    while (type != null) {
      // 遍历接口集合，若在 signatureMap 中，则添加到 interfaces 中
      for (Class<?> c : type.getInterfaces()) {
        // 必须是被@Signature注解的方法
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      // 获得父类
      type = type.getSuperclass();
    }
    // 创建接口的数组
    return interfaces.toArray(new Class<?>[0]);
  }

}
