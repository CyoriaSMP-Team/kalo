package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kalo.content.armor.ArmorDefinition;
import io.kalo.content.armor.ArmorSlot;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Bedrock attachable definitions — the half that puts custom armor on the player.
 *
 * <p>Java and Bedrock disagree completely here. Java paints an equipment texture onto the
 * player model through a pack-side equipment asset; Bedrock instead attaches a separate
 * <em>model</em> to the player, and hides the vanilla armor layer underneath it. So this
 * is not a translation of the Java output — it is a different mechanism producing the same
 * visual result, which is exactly the kind of thing the IR exists to allow.</p>
 *
 * <pre>
 * attachables/ruby_helmet.json
 * textures/models/armor/ruby_1.png     ← helmet, chestplate, boots
 * textures/models/armor/ruby_2.png     ← leggings, drawn on a different layer
 * </pre>
 */
public final class BedrockAttachable {

    private BedrockAttachable() {
    }

    /**
     * The vanilla armor geometry each slot attaches to.
     *
     * <p>Bedrock has one per piece rather than the two layers Java uses. These names are
     * vanilla's and must match exactly: an attachable naming a geometry that does not
     * exist draws nothing, and because {@link #hideVanillaLayer} has already switched off
     * the base material's own armor, the result is a piece that equips and is invisible.
     * That is precisely what a Bedrock player saw when these read
     * {@code geometry.player_armor.*}, which is not a geometry Bedrock defines.</p>
     */
    static @NotNull String geometryFor(@NotNull ArmorSlot slot) {
        return switch (slot) {
            case HEAD -> "geometry.humanoid.armor.helmet";
            case CHEST -> "geometry.humanoid.armor.chestplate";
            case LEGS -> "geometry.humanoid.armor.leggings";
            case FEET -> "geometry.humanoid.armor.boots";
        };
    }

    /**
     * The script that hides the vanilla layer this piece replaces.
     *
     * <p>Without it Bedrock draws both: the custom attachable and the base material's own
     * armor showing through underneath.</p>
     */
    static @NotNull String hideVanillaLayer(@NotNull ArmorSlot slot) {
        return switch (slot) {
            case HEAD -> "variable.helmet_layer_visible = 0.0;";
            case CHEST -> "variable.chest_layer_visible = 0.0;";
            case LEGS -> "variable.leg_layer_visible = 0.0;";
            case FEET -> "variable.boot_layer_visible = 0.0;";
        };
    }

    /**
     * Which of Bedrock's two armor texture sheets a slot draws from.
     *
     * <p>Leggings use sheet 2 and everything else sheet 1 — the same split Java makes
     * between its humanoid and humanoid_leggings layers, arrived at independently.</p>
     */
    static int textureLayer(@NotNull ArmorSlot slot) {
        return slot.usesLeggingsLayer() ? 2 : 1;
    }

    /** {@code textures/models/armor/<name>_<layer>} — no extension, as Bedrock expects. */
    static @NotNull String texturePath(@NotNull String name, int layer) {
        return "textures/models/armor/" + name + "_" + layer;
    }

    static @NotNull String attachablePath(@NotNull Key key) {
        return "attachables/" + key.namespace() + "_" + key.value() + ".json";
    }

    /**
     * Builds the attachable for one armor piece.
     *
     * @param textureName the shared armor sheet name, without the layer suffix
     */
    static @NotNull JsonObject attachable(@NotNull ArmorDefinition definition, @NotNull String textureName) {
        Key key = definition.key();
        ArmorSlot slot = definition.slot();

        JsonObject materials = new JsonObject();
        materials.addProperty("default", "armor");
        materials.addProperty("enchanted", "armor_enchanted");

        JsonObject textures = new JsonObject();
        textures.addProperty("default", texturePath(textureName, textureLayer(slot)));
        // Bedrock looks up the glint texture by this name; omitting it makes an enchanted
        // piece render untextured rather than simply unglinted.
        textures.addProperty("enchanted", "textures/misc/enchanted_actor_glint");

        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", geometryFor(slot));

        JsonArray renderControllers = new JsonArray();
        renderControllers.add("controller.render.armor");

        JsonObject scripts = new JsonObject();
        scripts.addProperty("parent_setup", hideVanillaLayer(slot));

        JsonObject description = new JsonObject();
        description.addProperty("identifier", key.namespace() + ":" + key.value());
        description.add("materials", materials);
        description.add("textures", textures);
        description.add("geometry", geometry);
        description.add("scripts", scripts);
        description.add("render_controllers", renderControllers);

        JsonObject attachable = new JsonObject();
        attachable.add("description", description);

        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("minecraft:attachable", attachable);
        return root;
    }
}
