package io.kalo.content.feature.event;

import io.kalo.pack.ResourcePack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public record ResourcePackGenerationEvent(
        @NotNull ResourcePack resourcePack
) implements FeatureEvent {
    @ApiStatus.Internal
    public ResourcePackGenerationEvent {
    }
}
