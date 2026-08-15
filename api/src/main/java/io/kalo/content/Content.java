package io.kalo.content;

import io.kalo.content.feature.Feature;
import io.kalo.content.feature.FeatureEventBus;
import net.kyori.adventure.key.Keyed;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public interface Content extends Keyed {
    @Unmodifiable
    @NotNull Collection<Feature> features();

    @NotNull FeatureEventBus featureEventBus();
}
