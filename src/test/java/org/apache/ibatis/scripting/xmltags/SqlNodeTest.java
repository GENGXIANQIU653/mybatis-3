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

package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SqlNodeTest {

  @Test
  public void testWhereSqlNode() throws IOException {

    String sqlFragment = "AND id = #{id}";
    // MixedSqlNode
    MixedSqlNode msn = new MixedSqlNode(Arrays.asList(new StaticTextSqlNode(sqlFragment)));

    WhereSqlNode wsn = new WhereSqlNode(new Configuration(), msn);

    DynamicContext dc = new DynamicContext(new Configuration(), new MapperMethod.ParamMap<>());

    wsn.apply(dc);

    System.out.println("解析前：" + sqlFragment);
    System.out.println("解析后：" + dc.getSql());
  }
}
