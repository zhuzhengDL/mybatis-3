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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 */
public class EnumOrdinalTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {
  /**
   * 枚举类
   */
  private final Class<E> type;
  /**
   * {@link #type} 下所有的枚举
   *
   * @see Class#getEnumConstants()
   */
  private final E[] enums;

  public EnumOrdinalTypeHandler(Class<E> type) {
    if (type == null) {
      throw new IllegalArgumentException("Type argument cannot be null");
    }
    this.type = type;
    this.enums = type.getEnumConstants();
    if (this.enums == null) {
      throw new IllegalArgumentException(type.getSimpleName() + " does not represent an enum type.");
    }
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
    // 将 Enum 转换成 int 类型
    ps.setInt(i, parameter.ordinal());
  }

  @Override
  public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
    // 将 int 转换成 Enum 类型
    int ordinal = rs.getInt(columnName);
    if (ordinal == 0 && rs.wasNull()) {
      return null;
    }
    // 将 int 转换成 Enum 类型
    return toOrdinalEnum(ordinal);
  }

  @Override
  public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    // 获得 int 的值
    int ordinal = rs.getInt(columnIndex);
    if (ordinal == 0 && rs.wasNull()) {
      return null;
    }
    // 将 int 转换成 Enum 类型
    return toOrdinalEnum(ordinal);
  }

  @Override
  public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    // 获得 int 的值
    int ordinal = cs.getInt(columnIndex);
    if (ordinal == 0 && cs.wasNull()) {
      return null;
    }
    // 将 int 转换成 Enum 类型
    return toOrdinalEnum(ordinal);
  }

  private E toOrdinalEnum(int ordinal) {
    try {
      //获取指定位置顺序的枚举值
      return enums[ordinal];
    } catch (Exception ex) {
      throw new IllegalArgumentException("Cannot convert " + ordinal + " to " + type.getSimpleName() + " by ordinal value.", ex);
    }
  }
}
