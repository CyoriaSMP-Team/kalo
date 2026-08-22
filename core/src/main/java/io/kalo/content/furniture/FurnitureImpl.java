package io.kalo.content.furniture;

import io.kalo.content.block.BlockImpl;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.furniture.definition.FurnitureDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FurnitureImpl extends BlockImpl implements Furniture {
    private final FurnitureDefinition furnitureDefinition;

    public FurnitureImpl(@NotNull FurnitureDefinition furnitureDefinition, @NotNull List<FeatureBuilder> features) {
        super(furnitureDefinition.toBlockDefinition(), features);
        this.furnitureDefinition = furnitureDefinition;
    }

    @Override
    public @NotNull FurnitureDefinition furnitureDefinition() {
        return furnitureDefinition;
    }
}
