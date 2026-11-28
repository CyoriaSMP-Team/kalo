package io.kalo.content.liquid;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps an item content key to the liquid its bucket places.
 *
 * <p>The item definition stays unchanged; a {@code liquid:} field on the item is enough to
 * make its bucket place a marked source block. The map is rebuilt with the content registry
 * on every reload.</p>
 */
public final class LiquidBucketRegistry {

    private static final Map<Key, Key> BUCKETS = new ConcurrentHashMap<>();
    private static final Map<Key, Key> ITEMS_BY_LIQUID = new ConcurrentHashMap<>();

    private LiquidBucketRegistry() {
    }

    public static void register(@NotNull Key item, @NotNull Key liquid) {
        BUCKETS.put(item, liquid);
        ITEMS_BY_LIQUID.putIfAbsent(liquid, item);
    }

    public static @Nullable Key liquid(@Nullable Key item) {
        return item == null ? null : BUCKETS.get(item);
    }

    /** The first registered bucket item for a liquid, or null when none exists. */
    public static @Nullable Key item(@Nullable Key liquid) {
        return liquid == null ? null : ITEMS_BY_LIQUID.get(liquid);
    }

    public static @NotNull Map<Key, Key> all() {
        return Map.copyOf(BUCKETS);
    }

    public static void clear() {
        BUCKETS.clear();
        ITEMS_BY_LIQUID.clear();
    }
}
