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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  /**
   * 执行解析原始 SQL ，成为 SqlSource 对象
   * @param originalSql  原始 SQL
   * @param parameterType 参数类型
   * @param additionalParameters  附加参数集合。可能是空集合，也可能是 {@link org.apache.ibatis.scripting.xmltags.DynamicContext#bindings} 集合
   * @return SqlSource 对象
   */
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    // <1> 创建 ParameterMappingTokenHandler 对象
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    // <2> 创建 GenericTokenParser 对象
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
    String sql;
    // <3> 执行解析
    if (configuration.isShrinkWhitespacesInSql()) {
      //在 Sql 中缩小空格
      sql = parser.parse(removeExtraWhitespaces(originalSql));
    } else {
      sql = parser.parse(originalSql);
    }
    // <4> 创建 StaticSqlSource 对象  最终都是转化成StaticSqlSource,类似sql语句：select * from table where id=?
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }

  /**
   * 删除额外的空格
   * @param original
   * @return
   */
  public static String removeExtraWhitespaces(String original) {
    StringTokenizer tokenizer = new StringTokenizer(original);
    StringBuilder builder = new StringBuilder();
    boolean hasMoreTokens = tokenizer.hasMoreTokens();
    while (hasMoreTokens) {
      builder.append(tokenizer.nextToken());
      hasMoreTokens = tokenizer.hasMoreTokens();
      if (hasMoreTokens) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

    /**
     * ParameterMapping 数组
     */
    private List<ParameterMapping> parameterMappings = new ArrayList<>();
    /**
     *  参数类型
     */
    private Class<?> parameterType;
    /**
     * additionalParameters 参数的对应的 MetaObject 对象
     */
    private MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      // 创建 additionalParameters 参数的对应的 MetaObject 对象
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    @Override
    public String handleToken(String content) {
      // <1> 构建 ParameterMapping 对象，并添加到 parameterMappings 中
      parameterMappings.add(buildParameterMapping(content));
      // <2> 返回 ? 占位符
      return "?";
    }

    /**
     *  #{age,javaType=int,jdbcType=NUMERIC,typeHandler=MyTypeHandler}
     *  ／／解析参数的属性，并形成 Map 例如＃｛ _frc_item_0 , javaType=int, jdbcType=NUMERIC,
     * II type andler=MyTypeHandler ｝这个占位符，它就会被解析成如下 Map:
     *
     * ／／｛ property ”－ frch_item一。 ，”javaType”-> ”int” , ” ] jdbcType ”->” NUMERIC ”,
     * // ” typeHandler " - > ” MyTypeHandler”}
     * @param content 为 #{ }之间的内容
     * @return
     */
    private ParameterMapping buildParameterMapping(String content) {
      // <1> 解析成 Map 集合
      Map<String, String> propertiesMap = parseParameterMapping(content);
      // <2> 获得属性的名字和类型
      String property = propertiesMap.get("property"); // 名字
      Class<?> propertyType;                      // 类型
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) {
        propertyType = Object.class;
      } else {
        MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
        if (metaClass.hasGetter(property)) {
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      }
      // <3> 创建 ParameterMapping.Builder 对象
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      // <3.1> 初始化 ParameterMapping.Builder 对象的属性
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }
      if (typeHandlerAlias != null) {
        // <3.2> 如果 typeHandlerAlias 非空，则获得对应的 TypeHandler 对象，并设置到 ParameterMapping.Builder 对象中
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      // <3.3> 创建 ParameterMapping 对象
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
