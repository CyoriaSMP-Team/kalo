package io.kalo.content.vehicle;

import io.kalo.content.AbstractContent;
import io.kalo.content.feature.FeatureBuilder;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class VehicleImpl extends AbstractContent implements Vehicle {
    @Getter @Accessors(fluent = true)
    private final VehicleDefinition definition;

    public VehicleImpl(@NotNull VehicleDefinition definition,
                       @NotNull List<FeatureBuilder> features) {
        super(definition.key(), features);
        this.definition = definition;
    }

    @Override
    public @NotNull String translationKey() {
        return definition.translationKey();
    }
}
