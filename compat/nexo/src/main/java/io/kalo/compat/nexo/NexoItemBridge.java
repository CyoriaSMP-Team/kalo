package io.kalo.compat.nexo;

import io.kalo.Kalo;
import io.kalo.items.ItemIdentity;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Nexo id semantics over Kalo's {@link ItemIdentity}.
 *
 * <p>Kept out of the {@code com.nexomc} package so the only thing the shim exposes to
 * consumers is the API surface they compiled against.</p>
 */
public final class NexoItemBridge {

    private NexoItemBridge() {
    }

    /** Resolves a Nexo id, namespaced or bare, to a fresh Kalo stack. */
    public static @Nullable ItemStack resolve(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        ItemIdentity identity = identity();
        if (identity == null) {
            return null;
        }
        Key key = id.indexOf(':') >= 0 ? parse(id) : BareIndex.resolve(id);
        if (key == null) {
            return null;
        }
        return identity.create(key).orElse(null);
    }

    /**
     * The Nexo id for a stack, or null when the stack is not Kalo content.
     *
     * <p>Returns the bare value when it is unambiguous, because Nexo configs address
     * items that way. A value defined by more than one pack falls back to the namespaced
     * key so the result still round trips through {@link #resolve(String)}.</p>
     */
    public static @Nullable String idOf(@Nullable ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemIdentity identity = identity();
        if (identity == null) {
            return null;
        }
        return identity.idOf(stack)
                .map(key -> BareIndex.isUnique(key) ? key.value() : key.asString())
                .orElse(null);
    }

    public static void rebuildIndex() {
        BareIndex.rebuild();
    }

    public static int indexSize() {
        return BareIndex.size();
    }

    private static @Nullable ItemIdentity identity() {
        try {
            return Kalo.plugin().items();
        } catch (RuntimeException notReady) {
            // Kalo has not enabled yet or is shutting down. A missing provider is a null
            // item, not an exception inside somebody else's click handler.
            return null;
        }
    }

    private static @Nullable Key parse(String id) {
        try {
            return Key.key(id);
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
