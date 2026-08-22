package io.kalo.content.painting;

import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.painting.definition.PaintingDefinition;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class PaintingRegistryEntryImpl implements PaintingRegistryEntry {
    private Key key;
    private List<FeatureBuilder> features = List.of();
    private PaintingDefinition definition;

    @Override public @NotNull PaintingRegistryEntry key(@NotNull Key key) { this.key = key; return this; }
    @Override public @NotNull PaintingRegistryEntry features(@NotNull List<FeatureBuilder> features) { this.features = features; return this; }
    @Override public @NotNull PaintingRegistryEntry definition(@NotNull PaintingDefinition definition) { this.definition = definition; return this; }

    @Override public @NotNull Painting toValue() {
        Objects.requireNonNull(definition, "definition was not set on the registry entry");
        if (key != null && !key.equals(definition.key())) {
            throw new IllegalStateException("Registry key " + key.asString() + " does not match definition key " + definition.key().asString());
        }
        return new PaintingImpl(definition, features);
    }
}
