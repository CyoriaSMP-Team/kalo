package io.kalo.items;

import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The item identity contract every consumer plugin should use instead of guessing from
 * material, display name or model data.
 *
 * <p>{@link #idOf(ItemStack)} answers "what is this?" for Kalo content and for external
 * content the server has learned. {@link #create(Key)} answers "give me that item" and is
 * guaranteed to succeed for every id {@code idOf} returned. A caller can therefore round
 * trip an item through storage and get the same stack back:</p>
 *
 * <pre>{@code
 * String id = Kalo.plugin().items().idOf(stack).map(Key::asString).orElse(null);
 * ItemStack restored = Kalo.plugin().items().create(Key.key(id)).orElseThrow();
 * }</pre>
 *
 * <p>Thread safety: implementations may read registries that are mutated during a reload.
 * Calls are safe from any thread, but an id observed during a reload may briefly stop
 * resolving.</p>
 */
public interface ItemIdentity {

    /** The content key of this stack, or empty when it is not custom content. */
    @NotNull Optional<Key> idOf(@NotNull ItemStack stack);

    /** A fresh stack for this content key, or empty when the key is unknown. */
    @NotNull Optional<ItemStack> create(@NotNull Key key);

    /** Whether this stack carries any recognised custom identity. */
    default boolean isCustom(@NotNull ItemStack stack) {
        return idOf(stack).isPresent();
    }
}
