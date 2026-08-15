package io.kalo.content.feature;

import io.kalo.content.Content;
import org.jetbrains.annotations.NotNull;

public record FeatureBuilder(
        @NotNull FeatureFactory<?> factory,
        @NotNull FeatureArguments arguments
) {
    public @NotNull Feature build(@NotNull Content content, @NotNull FeatureEventBus eventBus) {
        return factory.create(new FeatureFactory.Context(content, arguments, eventBus));
    }
}
