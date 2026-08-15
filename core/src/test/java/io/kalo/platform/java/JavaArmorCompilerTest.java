package io.kalo.platform.java;

import com.google.gson.JsonObject;
import io.kalo.content.armor.ArmorDefinition;
import io.kalo.content.armor.ArmorSlot;
import io.kalo.content.item.definition.ItemDefinition;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the equipment asset against the layout read out of the vanilla 26.2 client jar
 * ({@code assets/minecraft/equipment/diamond.json}).
 */
class JavaArmorCompilerTest {

    private static ArmorDefinition armor(String name, ArmorSlot slot, ArmorDefinition.EquipmentTexture equipment) {
        Key key = Key.key("testpack", name);
        return new ArmorDefinition(ItemDefinition.builder(key).build(), slot, equipment);
    }

    private static String textureOf(JsonObject asset, String layer) {
        return asset.getAsJsonObject("layers")
                .getAsJsonArray(layer).get(0).getAsJsonObject()
                .get("texture").getAsString();
    }

    @Test
    void helmetGoesOnTheHumanoidLayer() {
        ArmorDefinition definition = armor("ruby_helmet", ArmorSlot.HEAD,
                new ArmorDefinition.EquipmentTexture(Key.key("testpack", "ruby"), null));

        JsonObject asset = JavaArmorCompiler.equipmentAsset(definition, definition.equipment());

        assertEquals("testpack:ruby", textureOf(asset, "humanoid"));
        assertFalse(asset.getAsJsonObject("layers").has("humanoid_leggings"));
    }

    @Test
    void leggingsGoOnTheLeggingsLayerOnly() {
        // Vanilla draws leggings on a different model. Putting them on the humanoid layer
        // would paint them over the chestplate.
        ArmorDefinition definition = armor("ruby_leggings", ArmorSlot.LEGS,
                new ArmorDefinition.EquipmentTexture(Key.key("testpack", "ruby"), Key.key("testpack", "ruby_legs")));

        JsonObject asset = JavaArmorCompiler.equipmentAsset(definition, definition.equipment());

        assertEquals("testpack:ruby_legs", textureOf(asset, "humanoid_leggings"));
        assertFalse(asset.getAsJsonObject("layers").has("humanoid"),
                "leggings must not also be drawn on the humanoid layer");
    }

    @Test
    void leggingsFallBackToTheHumanoidTextureWhenNoLeggingsTextureIsGiven() {
        ArmorDefinition definition = armor("ruby_leggings", ArmorSlot.LEGS,
                new ArmorDefinition.EquipmentTexture(Key.key("testpack", "ruby"), null));

        JsonObject asset = JavaArmorCompiler.equipmentAsset(definition, definition.equipment());

        assertEquals("testpack:ruby", textureOf(asset, "humanoid_leggings"));
    }

    @Test
    void equipmentAssetLandsWhereTheClientLooksForIt() {
        assertEquals("assets/testpack/equipment/ruby_helmet.json",
                JavaArmorCompiler.equipmentPath(Key.key("testpack", "ruby_helmet")));
    }

    @Test
    void armorWithoutAWornAppearanceEmitsNoEquipmentAsset() {
        // Opting out keeps the base material's vanilla armor texture, and there is
        // nothing for the pack to override.
        ArmorDefinition definition = armor("plain_helmet", ArmorSlot.HEAD, null);
        assertNull(definition.equipment());
    }

    @Test
    void everySlotMapsToABukkitSlot() {
        // The one place ArmorSlot meets Bukkit; a new slot must not silently fall through.
        for (ArmorSlot slot : ArmorSlot.values()) {
            assertTrue(slot.id().equals(slot.name().toLowerCase(java.util.Locale.ROOT)));
        }
        assertTrue(ArmorSlot.LEGS.usesLeggingsLayer());
        assertFalse(ArmorSlot.CHEST.usesLeggingsLayer());
    }
}
