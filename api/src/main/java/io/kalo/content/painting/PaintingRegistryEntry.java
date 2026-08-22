package io.kalo.content.painting;

import io.kalo.content.ContentRegistryEntry;
import io.kalo.content.painting.definition.PaintingDefinition;
import org.jetbrains.annotations.NotNull;

public interface PaintingRegistryEntry extends ContentRegistryEntry<PaintingRegistryEntry, Painting> {
    @NotNull PaintingRegistryEntry definition(@NotNull PaintingDefinition definition);
}
