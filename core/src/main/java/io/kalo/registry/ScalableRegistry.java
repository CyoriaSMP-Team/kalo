package io.kalo.registry;

import it.unimi.dsi.fastutil.Pair;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry backing shared by the direct and entry-builder variants.
 *
 * <p>Writes use a concurrent map while packs are being assembled. Read-heavy gameplay uses
 * an immutable snapshot that is rebuilt at most once per revision. That avoids allocating a
 * fresh list for every {@link #entries()} or iterator call, and gives every entry a compact,
 * deterministic process-local runtime id for hot paths.</p>
 */
public abstract class ScalableRegistry<T> implements WritableRegistry<T>, RuntimeIdRegistry<T> {
    protected final Map<String, T> map = new ConcurrentHashMap<>();
    protected volatile boolean isLocked = false;

    private final AtomicLong revision = new AtomicLong();
    private volatile Snapshot<T> snapshot = Snapshot.empty();

    @Override
    public synchronized void lock() {
        snapshot = buildSnapshot(revision.get());
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

    /** Marks all cached read views stale after a successful mutation. */
    protected final void markDirty() {
        revision.incrementAndGet();
    }

    @Override
    public synchronized void merge(@NotNull Registry<T> registry) {
        checkWritable();

        // Staged so a conflict partway through leaves the registry untouched rather than
        // half-merged.
        Map<String, T> staged = new LinkedHashMap<>();
        for (Pair<Key, T> entry : registry.entries()) {
            String key = entry.key().asString();
            if (map.containsKey(key) || staged.containsKey(key)) {
                throw new IllegalStateException("Registry conflict on key '" + key + "'");
            }
            staged.put(key, entry.value());
        }
        if (!staged.isEmpty()) {
            map.putAll(staged);
            markDirty();
        }
    }

    @Override
    public synchronized void clear() {
        checkWritable();
        if (!map.isEmpty()) {
            map.clear();
            markDirty();
        }
    }

    @Override
    public @NotNull Optional<T> get(@NotNull Key key) {
        return Optional.ofNullable(map.get(key.asString()));
    }

    @Override
    public @NotNull @Unmodifiable Collection<Pair<Key, T>> entries() {
        return snapshotForRead().entries();
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return snapshotForRead().valuesByRuntimeId().iterator();
    }

    @Override
    public int runtimeId(@NotNull Key key) {
        return snapshotForRead().runtimeIdsByKey().getOrDefault(key.asString(), NO_RUNTIME_ID);
    }

    @Override
    public @NotNull Optional<T> getByRuntimeId(int runtimeId) {
        List<T> values = snapshotForRead().valuesByRuntimeId();
        if (runtimeId < 0 || runtimeId >= values.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(runtimeId));
    }

    @Override
    public long revision() {
        return revision.get();
    }

    /**
     * Returns one immutable view for the current revision.
     *
     * <p>The slow path is synchronized and only runs once after a write. During normal
     * gameplay registries are locked, so this is just two volatile reads.</p>
     */
    private @NotNull Snapshot<T> snapshotForRead() {
        long currentRevision = revision.get();
        Snapshot<T> current = snapshot;
        if (current.revision() == currentRevision) {
            return current;
        }
        synchronized (this) {
            currentRevision = revision.get();
            current = snapshot;
            if (current.revision() != currentRevision) {
                snapshot = current = buildSnapshot(currentRevision);
            }
            return current;
        }
    }

    /** Runtime ids are sorted by key so the same registry contents produce the same ids. */
    private @NotNull Snapshot<T> buildSnapshot(long snapshotRevision) {
        List<Map.Entry<String, T>> sorted = new ArrayList<>(map.entrySet());
        sorted.sort(Map.Entry.comparingByKey());

        List<Pair<Key, T>> entries = new ArrayList<>(sorted.size());
        List<T> values = new ArrayList<>(sorted.size());
        Map<String, Integer> runtimeIds = new LinkedHashMap<>(Math.max(16, sorted.size() * 2));

        int id = 0;
        for (Map.Entry<String, T> entry : sorted) {
            entries.add(Pair.of(Key.key(entry.getKey()), entry.getValue()));
            values.add(entry.getValue());
            runtimeIds.put(entry.getKey(), id++);
        }

        return new Snapshot<>(
                snapshotRevision,
                Collections.unmodifiableList(entries),
                Collections.unmodifiableList(values),
                Collections.unmodifiableMap(runtimeIds));
    }

    private record Snapshot<T>(
            long revision,
            List<Pair<Key, T>> entries,
            List<T> valuesByRuntimeId,
            Map<String, Integer> runtimeIdsByKey
    ) {
        private static <T> @NotNull Snapshot<T> empty() {
            return new Snapshot<>(-1L, List.of(), List.of(), Map.of());
        }
    }
}
