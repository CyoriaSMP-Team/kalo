package io.kalo.content.item;

import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.definition.ItemDefinition;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class ItemRegistryEntryImpl implements ItemRegistryEntry {
    private Key key;
    private List<FeatureBuilder> features = List.of();
    private ItemDefinition definition;

    @Override
    public @NotNull ItemRegistryEntry key(@NotNull Key key) {
        this.key = key;
        return this;
    }

    @Override
    public @NotNull ItemRegistryEntry features(@NotNull List<FeatureBuilder> features) {
        this.features = features;
        return this;
    }

    @Override
    public @NotNull ItemRegistryEntry definition(@NotNull ItemDefinition definition) {
        this.definition = definition;
        return this;
    }

    @Override
    public @NotNull Item toValue() {
        Objects.requireNonNull(definition, "definition was not set on the registry entry");
        if (key != null && !key.equals(definition.key())) {
            throw new IllegalStateException(
                    "Registry key " + key.asString() + " does not match definition key " + definition.key().asString());
        }
        return new ItemImpl(definition, features);
    }
}
