package com.dlz.kit.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * dlz-kit 最小子集：并发缓存 Map（PojoCache 元数据缓存使用）。
 * 语义：ConcurrentHashMap 委托（dlz-kit 原版为可配置容量/清理的缓存，本项目取最小语义）。
 */
public class CacheMap<K, V> implements Map<K, V> {

    private final Map<K, V> delegate = new ConcurrentHashMap<>();

    public CacheMap() {
    }

    public CacheMap(int initialCapacity) {
    }

    /** 取缓存，缺失时用 supplier 计算并写入（PojoCache 元数据惰性构建使用）。 */
    public V getAndSet(K key, Supplier<V> supplier) {
        V v = delegate.get(key);
        if (v == null && supplier != null) {
            v = supplier.get();
            if (v != null) {
                delegate.put(key, v);
            }
        }
        return v;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return delegate.get(key);
    }

    @Override
    public V put(K key, V value) {
        return delegate.put(key, value);
    }

    @Override
    public V remove(Object key) {
        return delegate.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        delegate.putAll(m);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }

    @Override
    public Collection<V> values() {
        return delegate.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return delegate.entrySet();
    }
}
