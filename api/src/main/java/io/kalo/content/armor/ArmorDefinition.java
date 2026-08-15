package io.kalo.content.armor;

import io.kalo.content.item.definition.ItemDefinition;
import org.jetbrains.annotations.NotNull;

public record ArmorDefinition(@NotNull ItemDefinition item, @NotNull ArmorSlot slot) {
    public @NotNull String translationKey() { return item.translationKey(); }
}
