package io.kalo.content.armor;

import io.kalo.content.feature.FeatureBuilder;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class ArmorRegistryEntryImpl implements ArmorRegistryEntry {
    private Key key;
    private List<FeatureBuilder> features = List.of();
    private ArmorDefinition definition;
    @Override public @NotNull ArmorRegistryEntry key(@NotNull Key key) { this.key = key; return this; }
    @Override public @NotNull ArmorRegistryEntry features(@NotNull List<FeatureBuilder> features) { this.features = features; return this; }
    @Override public @NotNull ArmorRegistryEntry definition(@NotNull ArmorDefinition definition) { this.definition = definition; return this; }
    @Override public @NotNull Armor toValue() {
        Objects.requireNonNull(definition, "definition was not set on the registry entry");
        return new ArmorImpl(definition, features);
    }
}
