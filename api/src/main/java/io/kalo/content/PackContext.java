package io.kalo.content;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Identity and location of the contents pack a piece of content is being loaded from.
 *
 * <p>Passed to {@link ContentType#load} so content keys are namespaced by their owning
 * pack. Without it, a definition named {@code ruby_sword} resolves to
 * {@code minecraft:ruby_sword} and collides with every other pack that happens to use
 * the same name.</p>
 *
 * @param namespace the pack id, used as the namespace for all content it defines
 * @param folder    the pack's directory on disk, the root for asset lookups
 */
public record PackContext(@NotNull String namespace, @NotNull File folder) {

    /** Namespaces a content name declared inside this pack. */
    public @NotNull Key key(@NotNull String name) {
        return Key.key(namespace, name);
    }

    /** Resolves a pack-relative asset path, e.g. {@code assets/item/ruby_sword.png}. */
    public @NotNull File file(@NotNull String relativePath) {
        return new File(folder, relativePath);
    }
}
