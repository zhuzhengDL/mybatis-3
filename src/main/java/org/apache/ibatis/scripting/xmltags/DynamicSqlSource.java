/**
 *    Copyright 2009-2019 the original author or authors.
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

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  /**
   * 根 SqlNode 对象
   */
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // <1> 应用 rootSqlNode
    //／／ 创建 DynamicContext 对象， parameterObject 是用户传入的实参
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    //／通过调用 ootSqlNode.apply （）方法调用整个树形结构中全部 SqlNode. apply （）方法，读者可以
    //／／ 体会一下组合设计模式的好处 每个 SqlNode apply （）方法都将解析得到的 SQL 语句片段追加到
    //context 中， 最终通过 context.getSql（）得到完整的 SQL 语句
    rootSqlNode.apply(context);
    // <2> 创建 SqlSourceBuilder 对象
    //／／创建 SqlSourceBuilder ，解析参数属性，并将 SQL 语句中的”＃{}占位符替换成？占位符
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // <2> 解析出 SqlSource 对象
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    // <3> 获得 BoundSql 对象
    //／／创建 BoundSql 对象 ，并将 DynamicContext.bindings 中的参数信息复制到其
    //additionalParameters 集合 中保存
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // <4> 添加附加参数到 BoundSql 对象中
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    // <5> 返回 BoundSql 对象
    return boundSql;
  }

}
