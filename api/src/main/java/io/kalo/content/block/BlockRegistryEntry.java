package io.kalo.content.block;

import io.kalo.content.ContentRegistryEntry;
import io.kalo.content.block.definition.BlockDefinition;
import org.jetbrains.annotations.NotNull;

public interface BlockRegistryEntry extends ContentRegistryEntry<BlockRegistryEntry, Block> {
    @NotNull BlockRegistryEntry definition(@NotNull BlockDefinition definition);
}
