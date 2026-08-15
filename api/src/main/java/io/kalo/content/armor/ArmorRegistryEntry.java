package io.kalo.content.armor;

import io.kalo.content.ContentRegistryEntry;
import org.jetbrains.annotations.NotNull;

public interface ArmorRegistryEntry extends ContentRegistryEntry<ArmorRegistryEntry, Armor> {
    @NotNull ArmorRegistryEntry definition(@NotNull ArmorDefinition definition);
}
