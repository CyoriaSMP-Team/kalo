package io.kalo.content.furniture;

import io.kalo.content.ContentRegistryEntry;
import io.kalo.content.furniture.definition.FurnitureDefinition;
import org.jetbrains.annotations.NotNull;

public interface FurnitureRegistryEntry extends ContentRegistryEntry<FurnitureRegistryEntry, Furniture> {
    @NotNull FurnitureRegistryEntry definition(@NotNull FurnitureDefinition definition);
}
