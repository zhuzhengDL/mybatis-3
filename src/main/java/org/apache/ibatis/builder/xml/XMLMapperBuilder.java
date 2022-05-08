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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {
  /**
   * 基于 Java XPath 解析器
   */
  private final XPathParser parser;
  /**
   * Mapper 构造器助手
   */
  private final MapperBuilderAssistant builderAssistant;
  /**
   * 可被其他语句引用的可重用语句块的集合
   *
   * 例如：<sql id="userColumns"> ${alias}.id,${alias}.username,${alias}.password </sql>
   */
  private final Map<String, XNode> sqlFragments;
  /**
   * 资源引用的地址
   */
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    // 创建 MapperBuilderAssistant 对象
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   * 解析mapper 绑定Mapper接口  构建MapperStatement
   */
  public void parse() {
    // <1> 判断当前 Mapper 是否已经加载过
    if (!configuration.isResourceLoaded(resource)) {
      // <2> 解析 `<mapper />` 节点
      configurationElement(parser.evalNode("/mapper"));
      // 将 resource 添加到 configuration 的 loadedResources属性 中，
      // 该属性是一个 HashSet<String>类型的集合，其中记录了已经加载过的映射文件
      // <3> 标记该 Mapper 已经加载过
      configuration.addLoadedResource(resource);
      // 注册 Mapper接口(同时会扫描Mapper接口的注解)
      //映射配置文件(Mapper.xml)与对应 Mapper 接口 的绑定
      // <4> 绑定 Mapper
      bindMapperForNamespace();
    }
    // <5> 处理 configurationElement()方法 中解析失败的 <resultMap>节点（因为依赖不全而失败）--重新解析之后再从列表中删除
    parsePendingResultMaps();
    //<6> 处理 configurationElement()方法 中解析失败的 <cacheRef>节点（因为依赖不全而失败）--重新解析之后再从列表中删除
    parsePendingCacheRefs();
    //  <7>  处理 configurationElement()方法 中解析失败的 <statement>节点（因为依赖不全而失败）--重新解析之后再从列表中删除
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  /**
   * 解析 <mapper>节点
    */
  private void configurationElement(XNode context) {
    try {
      // <1> 获得 namespace 属性
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.isEmpty()) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      // <1> 设置 namespace 属性
      builderAssistant.setCurrentNamespace(namespace);
      //<2> 解析<cache-ref>  二级缓存的依赖解析
      cacheRefElement(context.evalNode("cache-ref"));
      //<3> 解析<cache-ref>  二级缓存的
      cacheElement(context.evalNode("cache"));
      //解析<parameterMap></parameterMap>
      // 已废弃！老式风格的参数映射。内联参数是首选,这个元素可能在将来被移除，这里不会记录。
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      //<4> 解析<resultMap></resultMap>
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      //<5> 解析<sql></sql> 标签
      sqlElement(context.evalNodes("/mapper/sql"));
      // <6>    <select /> <insert /> <update /> <delete /> 节点们 解析sql语句标签  构建MapperStatement
      //会将之前解析的二级缓存对象包装到对应的MapperStatement
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
    // 上面两块代码，可以简写成 buildStatementFromContext(list, configuration.getDatabaseId());
  }

  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    // <1> 遍历 <select /> <insert /> <update /> <delete /> 节点们
    for (XNode context : list) {
      // <1> 创建 XMLStatementBuilder 对象，执行解析
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        //每一条sql语句转化为一个MapperStatement
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        // <2> 解析失败，添加到 configuration 中
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  private void parsePendingResultMaps() {
    // 获得 ResultMapResolver 集合，并遍历进行处理
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          // 执行解析
          iter.next().resolve();
          // 移除
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
          // 解析失败，仍然缺少资源，忽略
        }
      }
    }
  }

  private void parsePendingCacheRefs() {
    // 获得 CacheRefResolver 集合，并遍历进行处理
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          // 执行解析
          iter.next().resolveCacheRef();
          // 移除
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    // 获得 XMLStatementBuilder 集合，并遍历进行处理
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          // 执行解析
          iter.next().parseStatementNode();
          // 移除
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  /**
   *  <cache-ref namespace="com.someone.application.data.SomeMapper"/>
   * @param context
   */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      // <1> 获得指向的 namespace 名字，并添加到 configuration 的 cacheRefMap 中
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      // <2> 创建 CacheRefResolver 对象，并执行解析
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        //／解析 Cache 引用，该过程主要是设置 MapperBuil derAssistant 中的
        // currentCache和 unresolvedCacheRef字段
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        // <3> 解析失败，添加到 configuration 的 incompleteCacheRefs 中
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * <cache type="com.domain.something.MyCustomCache">
   *   <property name="cacheFile" value="/tmp/my-custom-cache.tmp"/>
   * </cache>
   * <cache eviction="FIFO" flushInterval="60000" size="512" readOnly="true"/>
   * @param context
   */
  private void cacheElement(XNode context) {
    if (context != null) {
      // <1> 获得负责存储的 Cache 实现类
      String type = context.getStringAttribute("type", "PERPETUAL");
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      // <2> 获得负责过期的 Cache 实现类
      String eviction = context.getStringAttribute("eviction", "LRU");
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      // <3> 获得 flushInterval、size、readWrite、blocking 属性
      Long flushInterval = context.getLongAttribute("flushInterval");
      Integer size = context.getIntAttribute("size");
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      boolean blocking = context.getBooleanAttribute("blocking", false);
      // <4> 获得 Properties 属性
      Properties props = context.getChildrenAsProperties();
      // <5> 创建 Cache 对象
      //／／通过 MapperBuilderAssistant 创建 Cache 对象，并添加到 Configuration caches 集合 保存
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }
   // 解析 <resultMap /> 节点们
  private void resultMapElements(List<XNode> list) {
    for (XNode resultMapNode : list) {
      try {
        // 处理单个 <resultMap /> 节点
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }
   // 解析 <resultMap /> 节点
  private ResultMap resultMapElement(XNode resultMapNode) {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  /** 解析 <resultMap /> 节点
   * <resultMap id="userResultMap" type="User">
   *   <id property="id" column="user_id" />
   *   <result property="username" column="user_name"/>
   *   <result property="password" column="hashed_password"/>
   * </resultMap>
   *
   * <resultMap id="vehicleResult" type="Vehicle">
   *    *   <id property="id" column="id" />
   *    *   <result property="vin" column="vin"/>
   *    *   <result property="year" column="year"/>
   *    *   <result property="make" column="make"/>
   *    *   <result property="model" column="model"/>
   *    *   <result property="color" column="color"/>
   *    *   <discriminator javaType="int" column="vehicle_type">
   *    *     <case value="1" resultMap="carResult"/>
   *    *     <case value="2" resultMap="truckResult"/>
   *    *     <case value="3" resultMap="vanResult"/>
   *    *     <case value="4" resultMap="suvResult"/>
   *    *   </discriminator>
   *    * </resultMap>
   * @param resultMapNode
   * @param additionalResultMappings
   * @param enclosingType
   * @return
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    // <1> 获得 id 属性
    String id = resultMapNode.getStringAttribute("id",
      resultMapNode.getValueBasedIdentifier());
    // <1> 获得 extends 属性
    String extend = resultMapNode.getStringAttribute("extends");
    // <1> 获得 autoMapping 属性
    //／／读取＜ resultMap ＞节点的 autoMapping 属性 将该属性设置为 true ，则启动自动映射功能，
    //／／即自动查找与列名同名的属性名，并调用 setter 方法 而设置为 false 后， 9)1
    //／／妥在 resultMap 节点内明确注明映射关系才会调用对应 setter 方法
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    // <1> 获得 type 属性
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    // <1> 解析 type 对应的类
    Class<?> typeClass = resolveClass(type);
    if (typeClass == null) {
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
    // <2> 创建 ResultMapping 集合 ／该集合用于记录解析的结采
    List<ResultMapping> resultMappings = new ArrayList<>(additionalResultMappings);
//／／处理resultMap 的子节点
    List<XNode> resultChildren = resultMapNode.getChildren();
    // <2> 遍历 <resultMap /> 的子节点
    for (XNode resultChild : resultChildren) {
      // <2.1> 处理 <constructor /> 节点
      //／／处理＜ constructor 节点
      if ("constructor".equals(resultChild.getName())) {
        processConstructorElement(resultChild, typeClass, resultMappings);
        // <2.2> 处理 <discriminator /> 节点
      } else if ("discriminator".equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        // <2.3> 处理其它节点 ／ 处理< id ＞、＜ result ＞、 association ＞、＜ collection ＞等节点
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);// 如果是＜id＞节点，则向 flags 集合中添加 ResultFlag ID
        }
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }

    // <3> 创建 ResultMapResolver 对象，执行解析
    //得到 resultMapping 对象集合之后，会调用 ResultMapResolver resolve （） 方法 该方法会调用
    //MapperBuilderAssistant.addResultMap （） 方法创建 ResultMap，并将 ResultMap 对象添加到
    //Configuration.resultMap 集合中保存。
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      //返回解析后的ResultMap对象
      return resultMapResolver.resolve();
    } catch (IncompleteElementException e) {
      // <4> 解析失败，添加到 configuration 中
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      return enclosingType;
    }
    return null;
  }

  /**
   *  <resultMap id="" type="">
   *     <constructor >
   *       <arg name=""></arg>
   *       <idArg resultMap="" name=""></idArg>
   *     </constructor>
   *   </resultMap>
   * @param resultChild
   * @param resultType
   * @param resultMappings
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) {
    List<XNode> argChildren = resultChild.getChildren();
    // <1> 遍历 <constructor /> 的子节点们
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<>();
      // <2> 获得 ResultFlag 集合
      flags.add(ResultFlag.CONSTRUCTOR);//／／添加 CONSTRUCTOR 标志
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);//II 对于＜ idArg ＞节点，添加ID 标志
      }
      // <3> 将当前子节点构建成 ResultMapping 对象，并添加到 resultMappings 中
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * <resultMap id="vehicleResult" type="Vehicle">
   *   <id property="id" column="id" />
   *   <result property="vin" column="vin"/>
   *   <result property="year" column="year"/>
   *   <result property="make" column="make"/>
   *   <result property="model" column="model"/>
   *   <result property="color" column="color"/>
   *   <discriminator javaType="int" column="vehicle_type">
   *     <case value="1" resultMap="carResult"/>
   *     <case value="2" resultMap="truckResult"/>
   *     <case value="3" resultMap="vanResult"/>
   *     <case value="4" resultMap="suvResult"/>
   *   </discriminator>
   * </resultMap>
   * @param context
   * @param resultType
   * @param resultMappings
   * @return
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) {
    // <1> 解析各种属性
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    // <1> 解析各种属性对应的类
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    Map<String, String> discriminatorMap = new HashMap<>();
    // <2> 遍历 <discriminator /> 的子节点，解析成 discriminatorMap 集合
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    // <3> 创建 Discriminator 对象
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
    // 上面两块代码，可以简写成 sqlElement(list, configuration.getDatabaseId());
  }

  /**
   * <sql id="sometable">
   *   ${prefix}Table
   * </sql>
   *
   * <sql id="someinclude">
   *   from
   *     <include refid="${include_target}"/>
   * </sql>
   * @param list
   * @param requiredDatabaseId
   */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    // <1> 遍历所有 <sql /> 节点
    for (XNode context : list) {
      // <2> 获得 databaseId 属性
      String databaseId = context.getStringAttribute("databaseId");
      String id = context.getStringAttribute("id");
      // <3> 获得完整的 id 属性，格式为 `${namespace}.${id}` 。
      id = builderAssistant.applyCurrentNamespace(id, false);
      // <4> 判断 databaseId 是否匹配
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        // <5> 添加到 sqlFragments 中
        //／／记录到 XMLMapperBuilder sqlFragments ( Map<String , XNode ＞类型）中保存，在
        //／XMLMapperBuilder 的构造函数中，可以看到该字段指向了 Configuration.sqlFragments 集合
        sqlFragments.put(id, context);
      }
    }
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      // 如果不匹配，则返回 false
      return requiredDatabaseId.equals(databaseId);
    }
    // 如果未设置 requiredDatabaseId ，但是 databaseId 存在，说明还是不匹配，则返回 false
    if (databaseId != null) {
      return false;
    }
    // 判断是否已经存在
    if (!this.sqlFragments.containsKey(id)) {
      return true;
    }
    // skip this fragment if there is a previous one with a not null databaseId
    XNode context = this.sqlFragments.get(id);
    // 若存在，则判断原有的 sqlFragment 是否 databaseId 为空。因为，当前 databaseId 为空，这样两者才能匹配。
    return context.getStringAttribute("databaseId") == null;
  }

  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) {
    // <1> 获得各种属性
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    String nestedResultMap = context.getStringAttribute("resultMap", () ->
        processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    // <1> 获得各种属性对应的类
    //解析 javaType typeHandler jdbcType
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // <2> 构建 ResultMapping 对象
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) {
    //／／ 只会处理 <association ＞、＜ collection ＞和＜ case ＞三种节点
    //／／指定select属性之后，不会生成嵌套的 ResultMap 对象  这三个节点不能指定select
    if (Arrays.asList("association", "collection", "case").contains(context.getName())
        && context.getStringAttribute("select") == null) {
      //校验返回类型是否存在集合对应的属性名的属性
      validateCollection(context, enclosingType);
      //解析嵌套的resultMap
      ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
      return resultMap.getId();
    }
    return null;
  }

  protected void validateCollection(XNode context, Class<?> enclosingType) {
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
        && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
            "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  /**
   * 映射配置文件(Mapper.xml)与对应 Mapper 接口 的绑定。(同时会扫描Mapper接口的注解)
   */
  private void bindMapperForNamespace() {
    // 获取映射配置文件的命名空间
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      // <1> 获得 Mapper 映射配置文件对应的 Mapper 接口，实际上类名就是 namespace
      Class<?> boundType = null;
      try {
        // 解析命名空间对应的类型
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        // ignore, bound type is not required
      }
      // 是否已加载 boundType接口(该 Mapper 接口)
      if (boundType != null && !configuration.hasMapper(boundType)) {
        // <2> 不存在该 Mapper 接口，则进行添加
        // Spring may not know the real resource name so we set a flag
        // to prevent loading again this resource from the mapper interface
        // look at MapperAnnotationBuilder#loadXmlResource
        // <3> 标记 namespace 已经添加，避免 MapperAnnotationBuilder#loadXmlResource(...) 重复加载
        // 追加个 "namespace:" 的前缀，并添加到 Configuration 的 loadedResources集合 中
        configuration.addLoadedResource("namespace:" + namespace);
        // 添加到 Configuration的mapperRegistry集合 中，另外，往这个方法栈的更深处看 会发现
        // 其创建了 MapperAnnotationBuilder对象，并调用了该对象的 parse()方法 解析 Mapper接口
        // <4> 添加到 configuration 中
        configuration.addMapper(boundType);
      }
    }
  }

}
