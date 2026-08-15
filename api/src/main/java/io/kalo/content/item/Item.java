package io.kalo.content.item;

import io.kalo.content.Content;
import io.kalo.content.item.definition.ItemDefinition;
import net.kyori.adventure.translation.Translatable;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface Item extends Content, Translatable, ItemLike {

    /** The platform-neutral definition this item was compiled from. */
    @NotNull ItemDefinition definition();

    /** The Java-platform stack compiled from {@link #definition()}. */
    @NotNull ImmutableItemStack itemStack();

    boolean isSimilar(@NotNull ItemStack itemStack);
}
