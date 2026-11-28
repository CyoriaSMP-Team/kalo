package io.kalo.content.mob;

import io.kalo.content.feature.FeatureBuilder;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class MobRegistryEntryImpl implements MobRegistryEntry {
    private Key key;
    private List<FeatureBuilder> features = List.of();
    private MobDefinition definition;

    @Override
    public @NotNull MobRegistryEntry key(@NotNull Key key) {
        this.key = key;
        return this;
    }

    @Override
    public @NotNull MobRegistryEntry features(@NotNull List<FeatureBuilder> features) {
        this.features = features;
        return this;
    }

    @Override
    public @NotNull MobRegistryEntry definition(@NotNull MobDefinition definition) {
        this.definition = definition;
        return this;
    }

    @Override
    public @NotNull Mob toValue() {
        Objects.requireNonNull(definition, "definition was not set on the registry entry");
        return new MobImpl(definition, features);
    }
}
