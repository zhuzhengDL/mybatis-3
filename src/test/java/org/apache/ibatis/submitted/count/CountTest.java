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
package org.apache.ibatis.submitted.count;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CountTest {

  protected static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void setUp() throws Exception {
    //初始化sqlSessionFactory
    try (Reader reader = Resources.getResourceAsReader("org/apache/ibatis/submitted/count/MapperConfig.xml")) {
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
    }

    BaseDataTest.runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
            "org/apache/ibatis/submitted/count/CreateDB.sql");
  }

  @Test
  void testCount() {
    //获取SqlSession
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      //获取代理对象
      CountMapper mapper = sqlSession.getMapper(CountMapper.class);
      //执行代理对象的函数
      int answer = mapper.count();
      //获取代理对象
      CountMapper mapper2 = sqlSession.getMapper(CountMapper.class);
      //执行代理对象的函数
      int answer2 = mapper2.count2();
      assertEquals(6, answer2);
    }
  }
}
