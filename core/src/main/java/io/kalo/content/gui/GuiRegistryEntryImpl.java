package io.kalo.content.gui;

import io.kalo.content.feature.FeatureBuilder;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class GuiRegistryEntryImpl implements GuiRegistryEntry {
    private Key key;
    private List<FeatureBuilder> features = List.of();
    private GuiDefinition definition;

    @Override public @NotNull GuiRegistryEntry key(@NotNull Key key) { this.key = key; return this; }
    @Override public @NotNull GuiRegistryEntry features(@NotNull List<FeatureBuilder> features) { this.features = features; return this; }
    @Override public @NotNull GuiRegistryEntry definition(@NotNull GuiDefinition definition) { this.definition = definition; return this; }

    @Override public @NotNull Gui toValue() {
        Objects.requireNonNull(definition, "definition was not set on the registry entry");
        if (key != null && !key.equals(definition.key())) {
            throw new IllegalStateException("Registry key " + key.asString() + " does not match definition key " + definition.key().asString());
        }
        return new GuiImpl(definition, features);
    }
}
