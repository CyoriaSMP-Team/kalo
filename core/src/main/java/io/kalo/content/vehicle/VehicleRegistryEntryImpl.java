package io.kalo.content.vehicle;

import io.kalo.content.feature.FeatureBuilder;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class VehicleRegistryEntryImpl implements VehicleRegistryEntry {
    private Key key;
    private List<FeatureBuilder> features = List.of();
    private VehicleDefinition definition;

    @Override public @NotNull VehicleRegistryEntry key(@NotNull Key key) { this.key = key; return this; }
    @Override public @NotNull VehicleRegistryEntry features(@NotNull List<FeatureBuilder> features) {
        this.features = features; return this;
    }
    @Override public @NotNull VehicleRegistryEntry definition(@NotNull VehicleDefinition definition) {
        this.definition = definition; return this;
    }
    @Override public @NotNull Vehicle toValue() {
        Objects.requireNonNull(definition, "definition was not set on the registry entry");
        return new VehicleImpl(definition, features);
    }
}
