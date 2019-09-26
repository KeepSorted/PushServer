package com.example.demo;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 双向HashMap， 可以实现O(1) 按值/键 查找、添加、删除元素对
 */
public class BiDirectionHashMap<K, V> {
    private ConcurrentHashMap<K, V> k2v; // key -> value
    private ConcurrentHashMap<V, K> v2k; // value -> key

    /**
     * 默认构造函数
     */
    BiDirectionHashMap() {
        this.k2v = new ConcurrentHashMap<>();
        this.v2k = new ConcurrentHashMap<>();
    }

    /**
     * 添加
     * @param k 键
     * @param v 值
     */
    public void put(K k, V v) {
        k2v.put(k, v);
        v2k.put(v, k);
    }

    /**
     * 查看大小
     * @return 大小
     */
    public int size () {
        return k2v.size();
    }

    /**
     * 是否有键
     * @param k 键
     * @return
     */
    public boolean containsKey(K k) {
        return k2v.containsKey(k);
    }

    /**
     * 是否有Value
     * @param v 值
     * @return
     */
    public boolean containsValue(V v) {
        return v2k.containsKey(v);
    }

    /**
     * 通过键删除
     * @param k 键
     * @return
     */
    public boolean removeByKey(K k) {
        if (!k2v.containsKey(k)) {
            return false;
        }

        V value = k2v.get(k);
        k2v.remove(k);
        v2k.remove(value);
        return true;
    }

    /**
     * 通过值删除
     * @param v 值
     * @return
     */
    public boolean removeByValue(V v) {
        if (!v2k.containsKey(v)) {
            return false;
        }

        K key = v2k.get(v);
        v2k.remove(v);
        k2v.remove(key);
        return true;
    }

    /**
     * 通过键获取值
     * @param k
     * @return
     */
    public V getByKey(K k) {
        return k2v.getOrDefault(k, null);
    }

    /**
     * 通过值获取键
     * @param v
     * @return
     */
    public K getByValue(V v) {
        return v2k.getOrDefault(v, null);
    }
}
