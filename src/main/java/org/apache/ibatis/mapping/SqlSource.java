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
package org.apache.ibatis.mapping;

/**
 * Represents the content of a mapped statement read from an XML file or an annotation.
 * It creates the SQL that will be passed to the database out of the input parameter received from the user.
 * 表示从 XML 文件或注释读取的映射语句的内容。
 * 它根据从用户收到的输入参数创建将传递到数据库的 SQL。
 * @author Clinton Begin
 */
public interface SqlSource {

  /**
   *  根据传入的参数对象，返回 BoundSql 对象
   *  getBoundSql （）方法会根据映射文件或注解描述的 SQL 语句，以及传入的参敛，返回可执行的 SQL
   * @param parameterObject parameterObject 参数对象
   * @return BoundSql 对象
   */
  BoundSql getBoundSql(Object parameterObject);

}
