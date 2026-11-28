package io.kalo.content.mob;

import io.kalo.content.AbstractContent;
import io.kalo.content.feature.FeatureBuilder;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class MobImpl extends AbstractContent implements Mob {

    @Getter @Accessors(fluent = true)
    private final MobDefinition definition;

    public MobImpl(@NotNull MobDefinition definition, @NotNull List<FeatureBuilder> features) {
        super(definition.key(), features);
        this.definition = definition;
    }

    @Override
    public @NotNull String translationKey() {
        return definition.translationKey();
    }
}
