package io.kalo.content.armor;

import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.ItemImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ArmorImpl extends ItemImpl implements Armor {
    private final ArmorDefinition armorDefinition;

    public ArmorImpl(@NotNull ArmorDefinition definition, @NotNull List<FeatureBuilder> features) {
        super(definition.item(), features);
        this.armorDefinition = definition;
    }

    @Override public @NotNull ArmorDefinition armorDefinition() { return armorDefinition; }
}
