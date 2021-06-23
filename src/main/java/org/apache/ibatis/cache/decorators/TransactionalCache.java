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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * @author 123
 * 实现 Cache 接口，支持事务的 Cache 实现类，主要用于二级缓存中
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  /**
   * 委托的 Cache 对象。
   *
   * 实际上，就是二级缓存 Cache 对象。
   */
  private final Cache delegate;

  /**
   * 提交时，清空 {@link #delegate}
   *
   * 初始时，该值为 false
   * 清理后{@link #clear()} 时，该值为 true ，表示持续处于清空状态
   */
  private boolean clearOnCommit;

  /**
   * 待提交的 KV 映射
   */
  private final Map<Object, Object> entriesToAddOnCommit;

  /**
   * 查找不到的 KEY 集合
   */
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // <1> 从 委托的 Cache 对象 delegate 中获取 key 对应的 value
    Object object = delegate.getObject(key);
    // <2> 如果不存在，则添加到 entriesMissedInCache 中
    if (object == null) {
      entriesMissedInCache.add(key);
    }
    // <3> 如果 clearOnCommit 为 true ，表示处于持续清空状态，则返回 null
    /**
     * 因为在事务未结束前，我们执行的清空缓存操作不好同步到 delegate 中，所以只好通过 clearOnCommit 来标记处于清空状态。
     * 那么，如果处于该状态，自然就不能返回 delegate 中查找的结果
     */
    if (clearOnCommit) {
      return null;
    }
    // <4> 返回 value
    else {
      return object;
    }
  }

  /**
   * 暂存 KV 到 entriesToAddOnCommit 中
   * @param key
   *          Can be any object but usually it is a CacheKey
   * @param object
   */
  @Override
  public void putObject(Object key, Object object) {
    // 添加到 map 中
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  /**
   * 清空缓存
   */
  @Override
  public void clear() {
    // 设定提交时清空缓存
    clearOnCommit = true;
    // 清空需要在提交时加入缓存的列表
    entriesToAddOnCommit.clear();
  }

  /**
   * 提交事务，重头戏
   */
  public void commit() {
    // <1> 如果 clearOnCommit 为 true ，则清空 delegate 缓存
    if (clearOnCommit) {
      delegate.clear();
    }
    /**
     * <2> 将 entriesToAddOnCommit、entriesMissedInCache 刷入 delegate，见detail
     */
    flushPendingEntries();

    reset();
  }

  public void rollback() {
    unlockMissedEntries();
    reset();
  }

  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 在flushPendingEntries中，将待提交的Map进行循环处理，委托给包装的Cache类，进行putObject的操作
   */
  private void flushPendingEntries() {

    // 将 entriesToAddOnCommit 刷入 delegate 中
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }

    // 将 entriesMissedInCache 刷入 delegate 中
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifying a rollback to the cache adapter. "
            + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
