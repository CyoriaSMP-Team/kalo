package io.kalo.content.musicdisc;

import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.musicdisc.definition.MusicDiscDefinition;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class MusicDiscRegistryEntryImpl implements MusicDiscRegistryEntry {
    private Key key;
    private List<FeatureBuilder> features = List.of();
    private MusicDiscDefinition definition;

    @Override public @NotNull MusicDiscRegistryEntry key(@NotNull Key key) { this.key = key; return this; }
    @Override public @NotNull MusicDiscRegistryEntry features(@NotNull List<FeatureBuilder> features) { this.features = features; return this; }
    @Override public @NotNull MusicDiscRegistryEntry definition(@NotNull MusicDiscDefinition definition) { this.definition = definition; return this; }

    @Override public @NotNull MusicDisc toValue() {
        Objects.requireNonNull(definition, "definition was not set on the registry entry");
        if (key != null && !key.equals(definition.key())) {
            throw new IllegalStateException("Registry key " + key.asString() + " does not match definition key " + definition.key().asString());
        }
        return new MusicDiscImpl(definition, features);
    }
}
