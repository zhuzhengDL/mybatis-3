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
package org.apache.ibatis.binding;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
public class MapperRegistry {
  // Mybatis 全局唯一的配置对象，包含了几乎所有配置信息
  private final Configuration config;
  // key：Mapper接口，value：MapperProxyFactory 为 Mapper接口 创建代理对象的工厂
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<>();
  // 初始化的时候会持有 Configuration对象
  public MapperRegistry(Configuration config) {
    this.config = config;
  }

  /**
   * 获取Mapper接口代理对象
   * @param type
   * @param sqlSession
   * @param <T>
   * @return
   */
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    //获取对应的代理对象工厂---解析配置的时候添加进来的Mapper,保存在Map中
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      //代理工厂生成代理对象
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }

  public <T> boolean hasMapper(Class<T> type) {
    //是否存在对应的mapper
    return knownMappers.containsKey(type);
  }

  //绑定Mapper  解析mybatis-config.xml的时候调用到
  public <T> void addMapper(Class<T> type) {
    // 该 type 是不是接口
    if (type.isInterface()) {
      // 是否已经加载过
      if (hasMapper(type)) {
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
        // 将 Mapper接口 的 Class对象 和 对应的 MapperProxyFactory对象 添加到 knownMappers集合
        knownMappers.put(type, new MapperProxyFactory<>(type));
        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        // 解析 Mapper接口 type 中的信息  注解扫描的形式
        //如果同一个方法同时在xml和注解中有配置，则会报错，
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        parser.parse();
        loadCompleted = true;
      } finally {
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }

  // 获取所有的 Mapper
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }
  // 下面的两个重载方法 通过扫描指定的包目录，获取所有的 Mapper接口 -解析mybatis-config.xml的时候调用到
  public void addMappers(String packageName) {
    addMappers(packageName, Object.class);
  }

  public void addMappers(String packageName, Class<?> superType) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
    for (Class<?> mapperClass : mapperSet) {
      addMapper(mapperClass);
    }
  }

}
