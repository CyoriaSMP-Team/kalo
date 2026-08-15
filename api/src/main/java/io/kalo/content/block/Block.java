package io.kalo.content.block;

import io.kalo.content.Content;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.item.ImmutableItemStack;
import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

/**
 * A registered custom block.
 *
 * <p>Named {@code Block} to match {@link io.kalo.content.item.Item}; where Bukkit's own
 * {@code org.bukkit.block.Block} is also in scope, qualify that one.</p>
 */
public interface Block extends Content, Translatable {

    /** The platform-neutral definition this block was compiled from. */
    @NotNull BlockDefinition definition();

    /** The item players place this block from. */
    @NotNull ImmutableItemStack itemStack();
}
