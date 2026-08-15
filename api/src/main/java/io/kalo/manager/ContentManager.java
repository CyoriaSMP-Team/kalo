package io.kalo.manager;

import io.kalo.content.item.Item;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface ContentManager {
    @NotNull Optional<Item> getItemByStack(@NotNull ItemStack itemStack);
}
