package io.kalo.content.block;

import io.kalo.content.AbstractContent;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.ImmutableItemStack;
import io.kalo.platform.java.JavaBlockItemCompiler;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BlockImpl extends AbstractContent implements Block {
    @Getter @Accessors(fluent = true)
    private final BlockDefinition definition;
    @Getter @Accessors(fluent = true)
    private final ImmutableItemStack itemStack;

    public BlockImpl(@NotNull BlockDefinition definition, @NotNull List<FeatureBuilder> features) {
        super(definition.key(), features);
        this.definition = definition;
        this.itemStack = ImmutableItemStack.of(JavaBlockItemCompiler.compile(this));
    }

    @Override
    public @NotNull String translationKey() {
        return definition.translationKey();
    }
}
