package io.kalo.content.feature;

import io.kalo.content.Content;
import org.jetbrains.annotations.NotNull;

public interface FeatureFactory<T extends Feature> {
    @NotNull T create(@NotNull Context context);

    record Context(
            @NotNull Content content,
            @NotNull FeatureArguments arguments,
            @NotNull FeatureEventBus eventBus
    ) {
    }
}
