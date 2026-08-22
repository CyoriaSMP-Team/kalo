package io.kalo.content.furniture;

import io.kalo.content.furniture.definition.FurnitureDefinition;
import io.kalo.content.feature.FeatureBuilder;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class FurnitureRegistryEntryImpl implements FurnitureRegistryEntry {
    private Key key;
    private List<FeatureBuilder> features = List.of();
    private FurnitureDefinition definition;

    @Override public @NotNull FurnitureRegistryEntry key(@NotNull Key key) { this.key = key; return this; }
    @Override public @NotNull FurnitureRegistryEntry features(@NotNull List<FeatureBuilder> features) { this.features = features; return this; }
    @Override public @NotNull FurnitureRegistryEntry definition(@NotNull FurnitureDefinition definition) { this.definition = definition; return this; }

    @Override public @NotNull Furniture toValue() {
        Objects.requireNonNull(definition, "definition was not set on the registry entry");
        if (key != null && !key.equals(definition.key())) {
            throw new IllegalStateException("Registry key " + key.asString() + " does not match definition key " + definition.key().asString());
        }
        return new FurnitureImpl(definition, features);
    }
}
