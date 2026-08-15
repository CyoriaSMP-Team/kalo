package io.kalo.content.block;

import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.feature.FeatureBuilder;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class BlockRegistryEntryImpl implements BlockRegistryEntry {
    private Key key;
    private List<FeatureBuilder> features = List.of();
    private BlockDefinition definition;

    @Override
    public @NotNull BlockRegistryEntry key(@NotNull Key key) {
        this.key = key;
        return this;
    }

    @Override
    public @NotNull BlockRegistryEntry features(@NotNull List<FeatureBuilder> features) {
        this.features = features;
        return this;
    }

    @Override
    public @NotNull BlockRegistryEntry definition(@NotNull BlockDefinition definition) {
        this.definition = definition;
        return this;
    }

    @Override
    public @NotNull Block toValue() {
        Objects.requireNonNull(definition, "definition was not set on the registry entry");
        if (key != null && !key.equals(definition.key())) {
            throw new IllegalStateException(
                    "Registry key " + key.asString() + " does not match definition key " + definition.key().asString());
        }
        return new BlockImpl(definition, features);
    }
}
