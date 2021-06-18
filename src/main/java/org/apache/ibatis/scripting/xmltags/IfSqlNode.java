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

/**
 * @author Clinton Begin
 *
 * IfSqlNode 对应的是 <if test=‘xxx’> 节点，<if> 节点是日常开发中使用频次比较高的一个节点。
 * IfSqlNode 的 apply 方法逻辑并不复杂，首先是通过 ONGL 检测 test 表达式是否为 true，如果为 true，则调用其他节点的 apply 方法继续进行解析。
 * 需要注意的是 <if> 节点中也可嵌套其他的动态节点，并非只有纯文本。
 * 因此 contents 变量遍历指向的是 MixedSqlNode，而非 StaticTextSqlNode
 */
public class IfSqlNode implements SqlNode {

  private final ExpressionEvaluator evaluator;

  /**
   * 判断表达式
   */
  private final String test;
  /**
   * 内嵌的 SqlNode 节点
   */
  private final SqlNode contents;

  public IfSqlNode(SqlNode contents, String test) {
    this.test = test;
    this.contents = contents;
    this.evaluator = new ExpressionEvaluator();
  }

  /**
   * 用来构造节点内的SQL语句
   */
  @Override
  public boolean apply(DynamicContext context) {
    // <1> 判断是否符合条件
    if (evaluator.evaluateBoolean(test, context.getBindings())) {
      // <2> 若 test 表达式中的条件成立，则调用其他节点的 apply 方法进行解析
      contents.apply(context);
      return true;
    }
    return false;
  }
}
