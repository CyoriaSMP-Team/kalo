package io.kalo.registry;

import it.unimi.dsi.fastutil.Pair;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry backing shared by the direct and entry-builder variants.
 *
 * <p>Reads happen from the pack generation thread while the main thread may still be
 * registering, so the backing map is concurrent and every check-then-act against the
 * locked flag is performed under the monitor.</p>
 */
public abstract class ScalableRegistry<T> implements WritableRegistry<T> {
    protected final Map<String, T> map = new ConcurrentHashMap<>();
    protected volatile boolean isLocked = false;

    @Override
    public synchronized void lock() {
        isLocked = true;
    }

    @Override
    public synchronized void unlock() {
        isLocked = false;
    }

    /** Throws if the registry is locked. Callers must hold this object's monitor. */
    protected void checkWritable() {
        if (isLocked) {
            throw new IllegalStateException("The registry is locked");
        }
    }

    @Override
    public synchronized void merge(@NotNull Registry<T> registry) {
        checkWritable();

        // Staged so a conflict partway through leaves the registry untouched rather than
        // half-merged.
        Map<String, T> staged = new java.util.LinkedHashMap<>();
        for (Pair<Key, T> entry : registry.entries()) {
            String key = entry.key().asString();
            if (map.containsKey(key) || staged.containsKey(key)) {
                throw new IllegalStateException("Registry conflict on key '" + key + "'");
            }
            staged.put(key, entry.value());
        }
        map.putAll(staged);
    }

    @Override
    public synchronized void clear() {
        checkWritable();
        map.clear();
    }

    @Override
    public @NotNull Optional<T> get(@NotNull Key key) {
        return Optional.ofNullable(map.get(key.asString()));
    }

    @Override
    public @NotNull @Unmodifiable Collection<Pair<Key, T>> entries() {
        List<Pair<Key, T>> result = new ArrayList<>(map.size());
        for (Map.Entry<String, T> entry : map.entrySet()) {
            result.add(Pair.of(Key.key(entry.getKey()), entry.getValue()));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return List.copyOf(map.values()).iterator();
    }
}
