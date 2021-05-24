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
package org.apache.ibatis.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation that specify the parameter name.
 *
 * <p>
 * <b>How to use:</b>
 *
 * <pre>
 * public interface UserMapper {
 *   &#064;Select("SELECT id, name FROM users WHERE name = #{name}")
 *   User selectById(&#064;Param("name") String value);
 * }
 * </pre>
 *
 * @author Clinton Begin
 *
 * 方法参数名的注解
 *
 * 当映射器方法需多个参数，这个注解可以被应用于映射器方法参数来给每个参数一个名字。
 * 否则，多参数将会以它们的顺序位置来被命名。比如 #{1}，#{2} 等，这是默认的。
 * 使用 @Param("person") ，SQL 中参数应该被命名为 #{person}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
// 参数
@Target(ElementType.PARAMETER)
public @interface Param {
  /**
   * Returns the parameter name.
   *
   * @return the parameter name
   */
  String value();
}
