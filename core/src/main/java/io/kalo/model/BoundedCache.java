package io.kalo.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Access-ordered LRU cache with a mutable capacity.
 *
 * <p>The model cache is static, so it outlives a plugin reload and cannot be replaced by
 * a fresh instance on pressure changes. {@link #resize(int)} is what lets the runtime
 * budget shrink it in place.</p>
 *
 * <p>All operations are synchronized: model loading happens on region threads, and an
 * access-ordered {@link LinkedHashMap} mutates its order on every read.</p>
 */
public final class BoundedCache<K, V> {

    private final LinkedHashMap<K, V> entries;
    private int capacity;

    public BoundedCache(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    public synchronized @Nullable V get(@NotNull K key) {
        return entries.get(key);
    }

    public synchronized void put(@NotNull K key, @NotNull V value) {
        entries.put(key, value);
        trim();
    }

    public synchronized void remove(@NotNull K key) {
        entries.remove(key);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized int capacity() {
        return capacity;
    }

    /** Applies a new capacity and evicts the least recently used entries immediately. */
    public synchronized void resize(int capacity) {
        this.capacity = Math.max(1, capacity);
        trim();
    }

    private void trim() {
        Iterator<Map.Entry<K, V>> iterator = entries.entrySet().iterator();
        while (entries.size() > capacity && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }
}
