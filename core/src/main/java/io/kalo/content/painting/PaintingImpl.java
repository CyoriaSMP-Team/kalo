package io.kalo.content.painting;

import io.kalo.content.AbstractContent;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.painting.definition.PaintingDefinition;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class PaintingImpl extends AbstractContent implements Painting {
    private final PaintingDefinition paintingDefinition;
    private final List<FeatureBuilder> featureBuilders;

    public PaintingImpl(@NotNull PaintingDefinition paintingDefinition, @NotNull List<FeatureBuilder> featureBuilders) {
        super(paintingDefinition.key(), featureBuilders);
        this.paintingDefinition = paintingDefinition;
        this.featureBuilders = featureBuilders;
    }

    @Override
    public @NotNull PaintingDefinition paintingDefinition() {
        return paintingDefinition;
    }
}
