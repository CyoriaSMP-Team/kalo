package io.kalo.platform.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kalo.content.armor.Armor;
import io.kalo.content.armor.ArmorDefinition;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Emits the assets that make custom armor visible <em>on the player</em>.
 *
 * <p>The item's own icon is handled by {@link JavaPackCompiler} like any other item. What
 * this adds is the equipment asset: {@code assets/<ns>/equipment/<name>.json}, which the
 * client resolves through the item's {@code minecraft:equippable} component and uses to
 * paint the armor onto the player model.</p>
 *
 * <p>Output for {@code testpack:ruby_helmet}:</p>
 * <pre>
 * assets/testpack/equipment/ruby_helmet.json
 *     {"layers":{"humanoid":[{"texture":"testpack:ruby_helmet"}]}}
 * </pre>
 *
 * <p>The layer texture itself is authored by the pack at
 * {@code assets/&lt;ns&gt;/textures/entity/equipment/humanoid/&lt;name&gt;.png} and copied in with
 * the pack's other assets — vanilla's own armor textures live under exactly that path.</p>
 */
public final class JavaArmorCompiler {
    private static final Logger LOGGER = Logger.getLogger(JavaArmorCompiler.class.getName());

    /** Vanilla's layer names. Leggings are drawn on a different model than the rest. */
    private static final String HUMANOID_LAYER = "humanoid";
    private static final String LEGGINGS_LAYER = "humanoid_leggings";

    private JavaArmorCompiler() {
    }

    public static void compileArmor(@NotNull ResourcePack pack, @NotNull Iterable<Armor> armors) {
        for (Armor armor : armors) {
            try {
                ArmorDefinition definition = armor.armorDefinition();
                ArmorDefinition.EquipmentTexture equipment = definition.equipment();
                if (equipment == null) {
                    // No worn appearance declared: the base material's vanilla armor
                    // texture is used, and there is no equipment asset to emit.
                    continue;
                }
                pack.file(equipmentPath(definition.key()), Json.writable(equipmentAsset(definition, equipment)));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to compile equipment assets for a piece of armor", e);
            }
        }
    }

    static @NotNull JsonObject equipmentAsset(@NotNull ArmorDefinition definition,
                                              @NotNull ArmorDefinition.EquipmentTexture equipment) {
        JsonObject layers = new JsonObject();

        if (definition.slot().usesLeggingsLayer()) {
            // Leggings are only ever drawn on the leggings layer; putting them on the
            // humanoid layer would paint them over the chestplate.
            Key texture = equipment.leggings() != null ? equipment.leggings() : equipment.humanoid();
            layers.add(LEGGINGS_LAYER, layer(texture));
        } else {
            layers.add(HUMANOID_LAYER, layer(equipment.humanoid()));
            if (equipment.leggings() != null) {
                layers.add(LEGGINGS_LAYER, layer(equipment.leggings()));
            }
        }

        JsonObject root = new JsonObject();
        root.add("layers", layers);
        return root;
    }

    private static @NotNull JsonArray layer(@NotNull Key texture) {
        JsonObject entry = new JsonObject();
        entry.addProperty("texture", texture.asString());

        JsonArray array = new JsonArray();
        array.add(entry);
        return array;
    }

    static @NotNull String equipmentPath(@NotNull Key key) {
        return "assets/" + key.namespace() + "/equipment/" + key.value() + ".json";
    }
}
