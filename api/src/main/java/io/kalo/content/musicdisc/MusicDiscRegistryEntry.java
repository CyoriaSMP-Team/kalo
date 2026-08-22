package io.kalo.content.musicdisc;

import io.kalo.content.ContentRegistryEntry;
import io.kalo.content.musicdisc.definition.MusicDiscDefinition;
import org.jetbrains.annotations.NotNull;

public interface MusicDiscRegistryEntry extends ContentRegistryEntry<MusicDiscRegistryEntry, MusicDisc> {
    @NotNull MusicDiscRegistryEntry definition(@NotNull MusicDiscDefinition definition);
}
