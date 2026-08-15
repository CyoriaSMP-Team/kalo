package io.kalo.content.furniture;

import io.kalo.content.block.BlockImpl;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.feature.FeatureBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FurnitureImpl extends BlockImpl implements Furniture {
    public FurnitureImpl(@NotNull BlockDefinition definition, @NotNull List<FeatureBuilder> features) {
        super(definition, features);
    }
}
