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
package org.apache.ibatis.session;

/**
 * Specifies if and how MyBatis should automatically map columns to fields/properties.
 * 自动映射行为的枚举
 * @author Eduardo Macarron
 */
public enum AutoMappingBehavior {

  /**
   * 禁用自动映射的功能
   * Disables auto-mapping.
   */
  NONE,

  /** 开启部分映射的功能
   * Will only auto-map results with no nested result mappings defined inside.
   */
  PARTIAL,

  /** 开启全部映射的功能
   * Will auto-map result mappings of any complexity (containing nested or otherwise).
   */
  FULL
}
