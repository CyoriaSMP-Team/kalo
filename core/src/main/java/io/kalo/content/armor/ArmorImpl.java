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
        super(definition.item(), features);
        this.armorDefinition = definition;
        // ItemImpl already built a plain item stack; rebuild it with the equippable
        // component so the piece can actually be worn.
        this.armorItemStack = ImmutableItemStack.of(JavaArmorItemCompiler.compile(this));
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
