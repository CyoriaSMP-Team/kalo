package io.kalo.content.vehicle;

import io.kalo.content.Content;
import org.jetbrains.annotations.NotNull;

/** A registered rideable vehicle. */
public interface Vehicle extends Content {
    @NotNull VehicleDefinition definition();

    @NotNull String translationKey();
}
