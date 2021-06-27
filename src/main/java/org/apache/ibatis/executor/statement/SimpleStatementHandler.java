/**
 *    Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * @author Clinton Begin
 */
public class SimpleStatementHandler extends BaseStatementHandler {

  public SimpleStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  @Override
  public int update(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    Object parameterObject = boundSql.getParameterObject();
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    int rows;
    // 如果是 Jdbc3KeyGenerator 类型
    if (keyGenerator instanceof Jdbc3KeyGenerator) {
      // <1.1> 执行写操作
      statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
      // <2.2> 获得更新数量
      rows = statement.getUpdateCount();
      // <1.3> 执行 keyGenerator 的后置处理逻辑
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
      // 如果是 SelectKeyGenerator 类型
    } else if (keyGenerator instanceof SelectKeyGenerator) {
      // <2.1> 执行写操作
      statement.execute(sql);
      // <2.2> 获得更新数量
      rows = statement.getUpdateCount();
      // <2.3> 执行 keyGenerator 的后置处理逻辑
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else {
      // <3.1> 执行写操作
      statement.execute(sql);
      // <3.2> 获得更新数量
      rows = statement.getUpdateCount();
    }
    return rows;
  }

  @Override
  public void batch(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    // 添加到批处理
    statement.addBatch(sql);
  }

  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    //获取静态SQL
    String sql = boundSql.getSql();
    // <1> 执行查询
    statement.execute(sql);
    // <2> 处理返回结果
    return resultSetHandler.handleResultSets(statement);
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    //获取静态SQL
    String sql = boundSql.getSql();
    // <1> 执行查询
    statement.execute(sql);
    // <2> 处理返回的 Cursor 结果
    return resultSetHandler.handleCursorResultSets(statement);
  }

  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
      //通过connection创建statement
      return connection.createStatement();
    } else {
      return connection.createStatement(mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    }
  }

  @Override
  public void parameterize(Statement statement) {
    // N/A
  }

}
