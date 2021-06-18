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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 * 通用的 Token 解析器, 用于解析形如 ${xxx}，#{xxx} 等标记
 * GenericTokenParser 负责将标记中的内容抽取出来，并将标记内容交给相应的 TokenHandler 去处理
 */
public class GenericTokenParser {

  /**
   * 开始的 Token 字符串
   */
  private final String openToken;

  /**
   * 结束的 Token 字符串
   */
  private final String closeToken;

  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  /**
   * 执行解析，取出token内容，即openToken 和 closeToken 中间内容
   * @param text
   * @return
   *
   * 例如
   * text = "SELECT * FROM Author WHERE age = #{age,javaType=int,jdbcType=NUMERIC}"
   */
  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    // search open token
    // 寻找开始的 openToken,即 "#{" 的位置， 此处start=33
    int start = text.indexOf(openToken);
    // 找不到，直接返回
    if (start == -1) {
      return text;
    }
    char[] src = text.toCharArray();
    // 起始查找位置
    int offset = 0;

    /**
     * 结果
     */
    final StringBuilder builder = new StringBuilder();
    // 匹配到 openToken 和 closeToken 之间的表达式
    StringBuilder expression = null;

    /**
     * 循环匹配
     */
    do {

      // 转义字符
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.

        /**
         * 因为 openToken 前面一个位置是 \ 转义字符，所以忽略 \
         * 添加 [offset, start - offset - 1] 和 openToken 的内容，添加到 builder 中
         */
        builder.append(src, offset, start - offset - 1).append(openToken);

        // 修改 offset
        offset = start + openToken.length();

      }
      // 非转义字符
      else {
        // found open token. let's search close token.
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        // builder = "SELECT * FROM Author WHERE age = "
        builder.append(src, offset, start - offset);

        // 修改 offset, 此处 offset = 35
        offset = start + openToken.length();

        // "}" 的index，此处 end = 68
        int end = text.indexOf(closeToken, offset);

        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
            // 此处 expression = "age,javaType=int,jdbcType=NUMERIC"
            expression.append(src, offset, end - offset);
            break;
          }
        }
        // end = 68
        if (end == -1) {
          // close token was not found.
          builder.append(src, start, src.length - start);
          offset = src.length;
        }
        // ===>>>> 执行此分支
        else {
          /**
           * 【重要】handler.handleToken(expression.toString())，将传入的参数解析成对应的 ParameterMapping 对象
           * handleToken 返回" ？"
           * 此处 builder = "SELECT * FROM Author WHERE age = ?"
           */
          builder.append(handler.handleToken(expression.toString()));
          // 此处 offset = 69
          offset = end + closeToken.length();
        }
      }
      // 此时 start = -1
      start = text.indexOf(openToken, offset);
    } while (start > -1);
    // false
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
