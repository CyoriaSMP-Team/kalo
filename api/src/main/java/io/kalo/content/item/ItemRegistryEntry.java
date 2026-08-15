package io.kalo.content.item;

import io.kalo.content.ContentRegistryEntry;
import io.kalo.content.item.definition.ItemDefinition;
import org.jetbrains.annotations.NotNull;

public interface ItemRegistryEntry extends ContentRegistryEntry<ItemRegistryEntry, Item> {
    @NotNull ItemRegistryEntry definition(@NotNull ItemDefinition definition);
}
