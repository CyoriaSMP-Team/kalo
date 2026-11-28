package io.kalo.content.emote;

import io.kalo.content.ContentRegistryEntry;
import org.jetbrains.annotations.NotNull;

public interface EmoteRegistryEntry extends ContentRegistryEntry<EmoteRegistryEntry, Emote> {
    @NotNull EmoteRegistryEntry definition(@NotNull EmoteDefinition definition);
}
