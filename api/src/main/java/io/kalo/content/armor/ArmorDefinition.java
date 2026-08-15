package io.kalo.content.armor;

import io.kalo.content.item.definition.ItemDefinition;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A wearable item.
 *
 * <p>Armor is an {@link ItemDefinition} plus the two things that make it armor: which
 * slot it occupies, and how it looks <em>on the player</em>. The second is a separate
 * texture from the item's own icon — the icon is what sits in the hotbar, the equipment
 * texture is what is painted onto the player model — and an armor piece that only has an
 * icon is not really armor.</p>
 *
 * @param item      the item form: icon, name, durability, base material
 * @param slot      where it is worn
 * @param equipment the worn appearance; {@code null} means the base material's vanilla
 *                  armor texture is used unchanged
 */
public record ArmorDefinition(
        @NotNull ItemDefinition item,
        @NotNull ArmorSlot slot,
        @Nullable EquipmentTexture equipment
) {

    public @NotNull Key key() {
        return item.key();
    }

    public @NotNull String translationKey() {
        return item.translationKey();
    }

    /**
     * The textures painted onto the player model.
     *
     * <p>Vanilla splits humanoid armor across two layers because leggings are drawn on a
     * different model than the rest, so a full set needs both. A piece that is not
     * leggings only needs {@link #humanoid()}.</p>
     *
     * @param humanoid  helmet, chestplate and boots layer
     * @param leggings  leggings layer; {@code null} for pieces that are not leggings
     */
    public record EquipmentTexture(@NotNull Key humanoid, @Nullable Key leggings) {
    }
}
