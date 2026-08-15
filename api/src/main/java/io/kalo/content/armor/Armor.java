package io.kalo.content.armor;

import io.kalo.content.item.Item;
import org.jetbrains.annotations.NotNull;

public interface Armor extends Item {
    @NotNull ArmorDefinition armorDefinition();
}
