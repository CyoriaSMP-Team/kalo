package io.kalo.platform.java;

import io.kalo.content.armor.Armor;
import io.kalo.content.armor.ArmorDefinition;
import io.kalo.content.armor.ArmorSlot;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the wearable half of a custom armor piece to its {@link ItemStack}.
 *
 * <p>{@link JavaItemCompiler} builds the item; this attaches the
 * {@code minecraft:equippable} component that tells the client which slot it goes in and
 * which equipment asset to paint onto the player. Without it the item is a
 * normal-looking icon that cannot be worn as anything custom.</p>
 *
 * <p>This is where {@link ArmorSlot} meets Bukkit's {@code EquipmentSlot}, and the only
 * place that mapping exists — the definition layer never names a Bukkit type.</p>
 */
public final class JavaArmorItemCompiler {

    private JavaArmorItemCompiler() {
    }

    public static @NotNull ItemStack compile(@NotNull Armor armor) {
        ItemStack itemStack = JavaItemCompiler.compile(armor);
        ArmorDefinition definition = armor.armorDefinition();

        itemStack.editMeta(meta -> {
            EquippableComponent equippable = meta.getEquippable();
            equippable.setSlot(toBukkit(definition.slot()));

            if (definition.equipment() != null) {
                // Points at assets/<ns>/equipment/<name>.json emitted by JavaArmorCompiler.
                Key key = definition.key();
                equippable.setModel(new NamespacedKey(key.namespace(), key.value()));
            }

            meta.setEquippable(equippable);
        });

        return itemStack;
    }

    static @NotNull EquipmentSlot toBukkit(@NotNull ArmorSlot slot) {
        return switch (slot) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
    }
}
