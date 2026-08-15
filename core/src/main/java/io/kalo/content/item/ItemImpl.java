package io.kalo.content.item;

import io.kalo.content.AbstractContent;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.platform.java.JavaItemCompiler;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class ItemImpl extends AbstractContent implements Item {
    @Getter @Accessors(fluent = true)
    private final ItemDefinition definition;
    @Getter @Accessors(fluent = true)
    private final ImmutableItemStack itemStack;

    public ItemImpl(@NotNull ItemDefinition definition, @NotNull List<FeatureBuilder> features) {
        super(definition.key(), features);
        this.definition = definition;
        this.itemStack = ImmutableItemStack.of(JavaItemCompiler.compile(this));
    }

    @Override
    public boolean isSimilar(@NotNull ItemStack itemStack) {
        return key().asString().equals(idOf(itemStack));
    }

    /**
     * Reads the Kalo item id stamped on a stack, or {@code null} if it is not one of ours.
     *
     * <p>Exposed so callers can resolve a stack with a single registry lookup instead of
     * testing it against every registered item in turn.</p>
     */
    public static String idOf(@NotNull ItemStack itemStack) {
        if (!itemStack.hasItemMeta()) {
            return null;
        }
        return Objects.requireNonNull(itemStack.getItemMeta())
                .getPersistentDataContainer()
                .get(JavaItemCompiler.ITEM_ID_KEY, PersistentDataType.STRING);
    }

    @Override
    public @NotNull String translationKey() {
        return definition.translationKey();
    }

    @Override
    public @NotNull Item asItem() {
        return this;
    }
}
