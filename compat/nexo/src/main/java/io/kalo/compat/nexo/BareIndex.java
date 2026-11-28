package io.kalo.compat.nexo;

import io.kalo.Kalo;
import io.kalo.content.armor.Armor;
import io.kalo.content.item.Item;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bare-id index over the live Kalo registries.
 *
 * <p>Nexo configs address items by value alone ({@code teddy_krathong}). Kalo keys are
 * namespaced by pack, so a bare id is only usable when exactly one pack defines that
 * value. An ambiguous value resolves to null instead of picking a pack at random.</p>
 */
final class BareIndex {

    /** A stale index costs one rebuild per second at most, never one per lookup. */
    private static final long REBUILD_FLOOR_NANOS = 1_000_000_000L;

    private static volatile Map<String, Key> unique = Map.of();
    private static volatile Set<String> duplicated = Set.of();
    private static volatile long lastRebuildNanos;

    private BareIndex() {
    }

    static synchronized void rebuild() {
        Map<String, Key> found = new HashMap<>();
        Set<String> clashes = new HashSet<>();
        try {
            for (Item item : Kalo.plugin().registryManager().registries().item()) {
                collect(item.key(), found, clashes);
            }
            for (Armor armor : Kalo.plugin().registryManager().registries().armor()) {
                collect(armor.key(), found, clashes);
            }
        } catch (RuntimeException notReady) {
            // Kalo is still loading or is disabling. Keep the previous snapshot rather
            // than publishing an empty one.
            return;
        }
        unique = Map.copyOf(found);
        duplicated = Set.copyOf(clashes);
        lastRebuildNanos = System.nanoTime();
    }

    static @Nullable Key resolve(@NotNull String bare) {
        Key hit = lookup(bare);
        if (hit != null) {
            return hit;
        }
        long now = System.nanoTime();
        if (now - lastRebuildNanos > REBUILD_FLOOR_NANOS) {
            rebuild();
            return lookup(bare);
        }
        return null;
    }

    static boolean isUnique(@NotNull Key key) {
        return !duplicated.contains(key.value());
    }

    static int size() {
        return unique.size();
    }

    private static @Nullable Key lookup(@NotNull String bare) {
        if (duplicated.contains(bare)) {
            return null;
        }
        return unique.get(bare);
    }

    private static void collect(Key key, Map<String, Key> found, Set<String> clashes) {
        if (clashes.contains(key.value())) {
            return;
        }
        Key previous = found.putIfAbsent(key.value(), key);
        if (previous != null) {
            found.remove(key.value());
            clashes.add(key.value());
        }
    }
}
