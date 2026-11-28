package io.kalo.compat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Other plugins use flat item ids ("ruby_sword"); Kalo keys are namespaced by pack
 * ("mypack:ruby_sword"). This is the one place that translates, so the rule is stated once
 * for every shim.
 *
 * <ul>
 *   <li>An id with a colon is taken as a full Kalo key.</li>
 *   <li>A bare id matches the Kalo key whose value is that id. If several packs define the same
 *       value the lexicographically first key wins, so the answer is stable across restarts
 *       instead of depending on load order.</li>
 * </ul>
 */
public final class CompatIds {
    private CompatIds() {
    }

    /** The flat id for a Kalo key string such as "mypack:ruby_sword". */
    public static @NotNull String fromKaloKey(@NotNull String key) {
        return key.substring(key.indexOf(':') + 1);
    }

    /** Picks the Kalo key an id refers to from {@code keys} ("namespace:value"), or null. */
    public static @Nullable String resolve(@NotNull String id, @NotNull Collection<String> keys) {
        if (id.isEmpty()) {
            return null;
        }
        if (id.indexOf(':') >= 0) {
            return keys.contains(id) ? id : null;
        }
        String best = null;
        for (String key : keys) {
            if (fromKaloKey(key).equals(id) && (best == null || key.compareTo(best) < 0)) {
                best = key;
            }
        }
        return best;
    }
}
