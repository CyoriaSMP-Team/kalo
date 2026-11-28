package io.kalo.content.mob;

import io.kalo.content.ContentRegistryEntry;
import org.jetbrains.annotations.NotNull;

public interface MobRegistryEntry extends ContentRegistryEntry<MobRegistryEntry, Mob> {
    @NotNull MobRegistryEntry definition(@NotNull MobDefinition definition);
}
