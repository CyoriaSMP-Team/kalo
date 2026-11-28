package io.kalo.content.emote;

import io.kalo.content.feature.FeatureBuilder;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class EmoteRegistryEntryImpl implements EmoteRegistryEntry {
    private Key key;
    private List<FeatureBuilder> features = List.of();
    private EmoteDefinition definition;

    @Override public @NotNull EmoteRegistryEntry key(@NotNull Key key) { this.key = key; return this; }
    @Override public @NotNull EmoteRegistryEntry features(@NotNull List<FeatureBuilder> features) {
        this.features = features; return this;
    }
    @Override public @NotNull EmoteRegistryEntry definition(@NotNull EmoteDefinition definition) {
        this.definition = definition; return this;
    }
    @Override public @NotNull Emote toValue() {
        Objects.requireNonNull(definition, "definition was not set on the registry entry");
        return new EmoteImpl(definition, features);
    }
}
