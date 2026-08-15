package io.kalo.content;

import io.kalo.content.feature.FeatureBuilder;
import io.kalo.registry.EntryWritableRegistry;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ContentRegistryEntry<SELF extends ContentRegistryEntry<SELF, T>, T extends Content> extends EntryWritableRegistry.RegistryEntry<T> {
    @NotNull SELF key(@NotNull Key key);

    @NotNull SELF features(@NotNull List<FeatureBuilder> features);
}
