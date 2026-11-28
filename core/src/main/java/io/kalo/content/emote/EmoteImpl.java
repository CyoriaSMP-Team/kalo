package io.kalo.content.emote;

import io.kalo.content.AbstractContent;
import io.kalo.content.feature.FeatureBuilder;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class EmoteImpl extends AbstractContent implements Emote {
    @Getter @Accessors(fluent = true)
    private final EmoteDefinition definition;

    public EmoteImpl(@NotNull EmoteDefinition definition,
                     @NotNull List<FeatureBuilder> features) {
        super(definition.key(), features);
        this.definition = definition;
    }

    @Override
    public @NotNull String translationKey() {
        return definition.translationKey();
    }
}
