package io.kalo.content.gui;

import io.kalo.content.AbstractContent;
import io.kalo.content.feature.FeatureBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class GuiImpl extends AbstractContent implements Gui {
    private final GuiDefinition guiDefinition;
    private final List<FeatureBuilder> featureBuilders;

    public GuiImpl(@NotNull GuiDefinition guiDefinition, @NotNull List<FeatureBuilder> featureBuilders) {
        super(guiDefinition.key(), featureBuilders);
        this.guiDefinition = guiDefinition;
        this.featureBuilders = featureBuilders;
    }

    @Override
    public @NotNull GuiDefinition guiDefinition() {
        return guiDefinition;
    }
}
