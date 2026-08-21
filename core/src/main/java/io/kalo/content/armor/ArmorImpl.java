package io.kalo.content.armor;

import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.ImmutableItemStack;
import io.kalo.content.item.ItemImpl;
import io.kalo.platform.java.JavaArmorItemCompiler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ArmorImpl extends ItemImpl implements Armor {
    private final ArmorDefinition armorDefinition;
    private final ImmutableItemStack armorItemStack;

    public ArmorImpl(@NotNull ArmorDefinition definition, @NotNull List<FeatureBuilder> features) {
        // Compile the completed armor stack in one pass. The old path built and emitted
        // an event for a plain item, threw that stack away, then built and emitted a
        // second event for the armor before its equippable component was attached.
        super(definition.item(), features,
                item -> JavaArmorItemCompiler.compile(item, definition));
        this.armorDefinition = definition;
        this.armorItemStack = super.itemStack();
    }

    @Override
    public @NotNull ArmorDefinition armorDefinition() {
        return armorDefinition;
    }

    @Override
    public @NotNull ImmutableItemStack itemStack() {
        return armorItemStack;
    }
}
