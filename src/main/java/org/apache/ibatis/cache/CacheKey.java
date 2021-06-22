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
package org.apache.ibatis.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.ibatis.reflection.ArrayUtil;

/**
 * @author Clinton Begin
 *
 * CacheKey 在一二级缓存中会被用到
 *
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;

  public static final CacheKey NULL_CACHE_KEY = new CacheKey() {

    @Override
    public void update(Object object) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }

    @Override
    public void updateAll(Object[] objects) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }
  };

  private static final int DEFAULT_MULTIPLIER = 37;

  private static final int DEFAULT_HASHCODE = 17;

  /**
   * 乘子，默认为37
   */
  private final int multiplier;

  /**
   * CacheKey 的 hashCode，综合了各种影响因子
   */
  private int hashcode;

  /**
   * 校验和
   */
  private long checksum;

  /**
   * 影响因子个数
   */
  private int count;

  /**
   * 影响因子集合
   */
  private List<Object> updateList;

  public CacheKey() {
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLIER;
    this.count = 0;
    this.updateList = new ArrayList<>();
  }

  public CacheKey(Object[] objects) {
    this();
    updateAll(objects);
  }

  public int getUpdateCount() {
    return updateList.size();
  }

  /**
   * 每当执行更新操作时，表示有新的影响因子参与计算
   *
   * 当不断有新的影响因子参与计算时，hashcode 和 checksum 将会变得愈发复杂和随机。这样可降低冲突率，使 CacheKey 可在缓存中更均匀的分布
   *
   * @param object
   */
  public void update(Object object) {

    int baseHashCode = object == null ? 1 : ArrayUtil.hashCode(object);

    // 自增 count
    count++;

    // 计算校验和
    checksum += baseHashCode;

    // 更新 baseHashCode
    baseHashCode *= count;

    // 计算 hashCode
    hashcode = multiplier * hashcode + baseHashCode;

    // 保存影响因子
    updateList.add(object);
  }

  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  /**
   * CacheKey 最终要作为键存入 HashMap，因此它需要覆盖 equals 和 hashCode 方法
   * @param object
   * @return
   */
  @Override
  public boolean equals(Object object) {
    // 检测是否为同一个对象
    if (this == object) {
      return true;
    }

    // 检测 object 是否为 CacheKey
    if (!(object instanceof CacheKey)) {
      return false;
    }

    final CacheKey cacheKey = (CacheKey) object;

    // 检测 hashCode 是否相等
    if (hashcode != cacheKey.hashcode) {
      return false;
    }

    // 检测校验和是否相同
    if (checksum != cacheKey.checksum) {
      return false;
    }

    // 检测 count 是否相同
    if (count != cacheKey.count) {
      return false;
    }

    // 如果上面的检测都通过了，下面分别对每个影响因子进行比较
    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (!ArrayUtil.equals(thisObject, thatObject)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  @Override
  public String toString() {
    StringJoiner returnValue = new StringJoiner(":");
    returnValue.add(String.valueOf(hashcode));
    returnValue.add(String.valueOf(checksum));
    updateList.stream().map(ArrayUtil::toString).forEach(returnValue::add);
    return returnValue.toString();
  }

  @Override
  public CacheKey clone() throws CloneNotSupportedException {
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    clonedCacheKey.updateList = new ArrayList<>(updateList);
    return clonedCacheKey;
  }

}
