package com.nexomc.nexo.items;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Nexo's item builder, reduced to the single method consumer plugins call.
 *
 * <p>Written from the consumer bytecode references, not from Nexo's source. The type and
 * the {@code build()} descriptor are what matter: a consumer compiled against Nexo links
 * on {@code itemFromId(String)Lcom/nexomc/nexo/items/ItemBuilder;} and then calls
 * {@code build()}.</p>
 */
public final class ItemBuilder {

    private final ItemStack stack;

    public ItemBuilder(@NotNull ItemStack stack) {
        this.stack = stack.clone();
    }

    public @NotNull ItemStack build() {
        return stack.clone();
    }
}
