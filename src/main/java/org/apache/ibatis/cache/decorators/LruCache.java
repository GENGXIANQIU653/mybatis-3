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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (least recently used) cache decorator.
 *
 * @author Clinton Begin
 *
 * 具有 LRU 策略的缓存
 *
 * LruCache 的 keyMap 属性是实现 LRU 策略的关键，该属性类型继承自 LinkedHashMap，并覆盖了 removeEldestEntry 方法。
 *
 * LinkedHashMap 可保持键值对的插入顺序，当插入一个新的键值对时，LinkedHashMap 内部的 tail 节点会指向最新插入的节点。
 * head 节点则指向第一个被插入的键值对，也就是最久未被访问的那个键值对。
 *
 * 默认情况下，LinkedHashMap 仅维护键值对的插入顺序。
 *
 * 若要基于 LinkedHashMap 实现 LRU 缓存，还需通过构造方法将 LinkedHashMap 的 accessOrder 属性设为 true，
 * 此时 LinkedHashMap 会维护键值对的访问顺序。
 *
 * getObject() 方法中执行了这样一句代码 keyMap.get(key)，目的是刷新 key 对应的键值对在 LinkedHashMap 的位置。
 * LinkedHashMap 会将 key 对应的键值对移动到链表的尾部，尾部节点表示最近刚被访问过或者插入的节点。
 *
 * 除了需将 accessOrder 设为 true，还需覆盖 removeEldestEntry 方法。
 * LinkedHashMap 在插入新的键值对时会调用该方法，以决定是否在插入新的键值对后，移除老的键值对。
 * 当被装饰类的容量超出了 keyMap 的所规定的容量（由构造方法传入）后，keyMap 会移除最长时间未被访问的键，并保存到 eldestKey 中，
 * 然后由 cycleKeyList 方法将 eldestKey 传给被装饰类的 removeObject 方法，移除相应的缓存项目
 */
public class LruCache implements Cache {


  private final Cache delegate;

  /**
   * keyMap 属性是实现 LRU 策略的关键，该属性类型继承自 LinkedHashMap，并覆盖了 removeEldestEntry 方法
   */
  private Map<Object, Object> keyMap;


  private Object eldestKey;

  public LruCache(Cache delegate) {
    this.delegate = delegate;
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(final int size) {

    /**
     * 初始化 keyMap，注意，keyMap 的类型继承自 LinkedHashMap，
     * 并覆盖了 removeEldestEntry 方法
     */
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;

      // 覆盖 LinkedHashMap 的 removeEldestEntry 方法
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) {
          // 获取将要被移除缓存项的键值
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  @Override
  public void putObject(Object key, Object value) {
    // 存储缓存项
    delegate.putObject(key, value);
    cycleKeyList(key);
  }

  @Override
  public Object getObject(Object key) {
    // 刷新 key 在 keyMap 中的位置
    keyMap.get(key);
    // 从被装饰类中获取相应缓存项
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    // 从被装饰类中移除相应的缓存项
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  private void cycleKeyList(Object key) {
    // 存储 key 到 keyMap 中
    keyMap.put(key, key);
    if (eldestKey != null) {
      // 从被装饰类中移除相应的缓存项
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

}
