package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kalo.content.armor.ArmorDefinition;
import io.kalo.content.armor.ArmorSlot;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Builds Bedrock attachable files: the mechanism that puts custom armor, held 3D items and
 * emotes on a Bedrock player.
 *
 * <p>Java and Bedrock reach the same look by different routes. Java repaints the player
 * model with an equipment texture; Bedrock binds a separate model to the entity and turns
 * the vanilla armor layer off. So this is not a port of Java pack output — it is a second
 * rendering of the same definition, which is the kind of platform split the IR exists to
 * allow.</p>
 *
 * <p>Armor sheets follow vanilla's two-sheet split: helmet, chestplate and boots sample
 * sheet 1, leggings sample sheet 2.</p>
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
     * The vanilla geometry a slot's attachable binds to.
     *
     * <p>Bedrock has one built-in geometry per armor piece, and the string has to match
     * exactly. A name Bedrock does not define produces an attachable that loads but draws
     * nothing — and because {@link #hideVanillaLayer} has already hidden the default
     * armor, the player ends up looking unarmored. {@code geometry.player_armor.*} is an
     * example of a plausible name that no Bedrock version ships.</p>
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
     * The {@code parent_setup} snippet that hides the vanilla layer this piece replaces.
     *
     * <p>Without it both are drawn: the custom attachable on top of the default armor.</p>
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
     * Which of the two vanilla armor sheets a slot samples. Only leggings use sheet 2,
     * matching vanilla's separate leggings model.
     */
    static int textureLayer(@NotNull ArmorSlot slot) {
        return slot.usesLeggingsLayer() ? 2 : 1;
    }

    /** Texture reference as Bedrock wants it: pack-relative, without a file extension. */
    static @NotNull String texturePath(@NotNull String name, int layer) {
        return "textures/models/armor/" + name + "_" + layer;
    }

    /** Path of the attachable file inside the pack. */
    static @NotNull String attachablePath(@NotNull Key key) {
        return "attachables/" + key.namespace() + "_" + key.value() + ".json";
    }

    /**
     * A static 3D item rather than armor: bind its geometry to the holder's item slot.
     * The geometry's single bone carries the Bedrock item-slot binding.
     */
    static @NotNull JsonObject heldItem(@NotNull Key item, @NotNull String geometryId,
                                        @NotNull String texturePath) {
        return heldItem(item, geometryId, texturePath, Map.of(), Map.of(), List.of());
    }

    /**
     * Held item that keeps every material instance. An imported Blockbench model can point
     * different faces at different textures, and each material key has to be declared
     * here; collapsing them onto one texture would lose that split.
     */
    static @NotNull JsonObject heldItem(@NotNull Key item, @NotNull String geometryId,
                                        @NotNull String defaultTexturePath,
                                        @NotNull Map<String, String> materialTexturePaths) {
        return heldItem(item, geometryId, defaultTexturePath, materialTexturePaths,
                Map.of(), List.of());
    }

    /**
     * Held item with authored Blockbench animations. {@code authoredAnimations} maps the
     * short clip name used in the attachable to its full animation id; {@code autoAnimate}
     * lists clips that should start immediately, without waiting for a trigger.
     */
    static @NotNull JsonObject heldItem(@NotNull Key item, @NotNull String geometryId,
                                        @NotNull String defaultTexturePath,
                                        @NotNull Map<String, String> materialTexturePaths,
                                        @NotNull Map<String, String> authoredAnimations,
                                        @NotNull List<String> autoAnimate) {
        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest");
        materials.addProperty("enchanted", "entity_alphatest_glint");
        JsonObject textures = new JsonObject();
        textures.addProperty("default", defaultTexturePath);
        textures.addProperty("enchanted", "textures/misc/enchanted_item_glint");
        materialTexturePaths.forEach((material, path) -> {
            if (material.equals("default") || material.equals("enchanted")) {
                return;
            }
            materials.addProperty(material, "entity_alphatest");
            textures.addProperty(material, path);
        });

        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", geometryId);

        // The hold clips generated from display transforms replace vanilla's steve_head
        // clips, which do not place a custom geometry in the hand. With no clip at all the
        // model sits at the bare hand-bone transform, which reads as floating or
        // wrongly sized, so the vanilla clips stay as a fallback.
        JsonObject animations = new JsonObject();
        authoredAnimations.forEach(animations::addProperty);
        if (!animations.has("hold_first_person")) {
            animations.addProperty("hold_first_person",
                    "animation.steve_head.hold_first_person");
        }
        if (!animations.has("hold_third_person")) {
            animations.addProperty("hold_third_person",
                    "animation.steve_head.hold_third_person");
        }
        JsonObject firstPerson = new JsonObject();
        firstPerson.addProperty("hold_first_person", "context.is_first_person == 1.0");
        JsonObject thirdPerson = new JsonObject();
        thirdPerson.addProperty("hold_third_person", "context.is_first_person == 0.0");
        JsonArray animate = new JsonArray();
        animate.add(firstPerson);
        animate.add(thirdPerson);
        for (String animation : autoAnimate) {
            if (animations.has(animation)) {
                animate.add(animation);
            }
        }
        JsonObject scripts = new JsonObject();
        scripts.add("animate", animate);

        JsonArray controllers = new JsonArray();
        controllers.add("controller.render.item_default");

        JsonObject description = new JsonObject();
        description.addProperty("identifier", item.asString());
        description.add("materials", materials);
        description.add("textures", textures);
        description.add("geometry", geometry);
        description.add("animations", animations);
        description.add("scripts", scripts);
        description.add("render_controllers", controllers);

        JsonObject attachable = new JsonObject();
        attachable.add("description", description);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("minecraft:attachable", attachable);
        return root;
    }

    /**
     * A player-body emote asset. It carries the same geometry, textures and animations as
     * the client entity Kalo spawns, so a Bedrock pack can bind the model to the player
     * itself.
     */
    static @NotNull JsonObject playerEmote(
            @NotNull Key key,
            @NotNull String geometryId,
            @NotNull String defaultTexture,
            @NotNull Map<String, String> materialTextures,
            @NotNull Map<String, String> animations,
            @NotNull List<String> loops) {
        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest");
        materials.addProperty("enchanted", "entity_alphatest_glint");
        JsonObject textures = new JsonObject();
        textures.addProperty("default", defaultTexture);
        textures.addProperty("enchanted", "textures/misc/enchanted_item_glint");
        materialTextures.forEach((material, path) -> {
            if (material.equals("default") || material.equals("enchanted")) {
                return;
            }
            materials.addProperty(material, "entity_alphatest");
            textures.addProperty(material, path);
        });
        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", geometryId);

        JsonObject description = new JsonObject();
        description.addProperty("identifier", key.namespace() + ":" + key.value() + "_emote");
        description.add("materials", materials);
        description.add("textures", textures);
        description.add("geometry", geometry);
        if (!animations.isEmpty()) {
            JsonObject table = new JsonObject();
            animations.forEach(table::addProperty);
            description.add("animations", table);
        }
        if (!loops.isEmpty()) {
            JsonArray animate = new JsonArray();
            loops.forEach(animate::add);
            JsonObject scripts = new JsonObject();
            scripts.add("animate", animate);
            description.add("scripts", scripts);
        }
        JsonArray renderControllers = new JsonArray();
        renderControllers.add("controller.render.default");
        description.add("render_controllers", renderControllers);

        JsonObject attachable = new JsonObject();
        attachable.add("description", description);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("minecraft:attachable", attachable);
        return root;
    }

    /**
     * Worn 3D armor: the authored geometry takes vanilla's place, and the same
     * {@code parent_setup} hides the layer beneath it.
     */
    static @NotNull JsonObject wornModel(
            @NotNull ArmorDefinition definition,
            @NotNull String geometryId,
            @NotNull String defaultTexture,
            @NotNull Map<String, String> materialTextures,
            @NotNull Map<String, String> animations,
            @NotNull List<String> loops) {
        Key key = definition.key();
        ArmorSlot slot = definition.slot();

        JsonObject materials = new JsonObject();
        materials.addProperty("default", "armor");
        materials.addProperty("enchanted", "armor_enchanted");
        JsonObject textures = new JsonObject();
        textures.addProperty("default", defaultTexture);
        textures.addProperty("enchanted", "textures/misc/enchanted_actor_glint");
        materialTextures.forEach((material, path) -> {
            if (material.equals("default") || material.equals("enchanted")) {
                return;
            }
            materials.addProperty(material, "armor");
            textures.addProperty(material, path);
        });

        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", geometryId);

        JsonObject scripts = new JsonObject();
        scripts.addProperty("parent_setup", hideVanillaLayer(slot));

        JsonObject description = new JsonObject();
        description.addProperty("identifier", key.namespace() + ":" + key.value());
        description.add("materials", materials);
        description.add("textures", textures);
        description.add("geometry", geometry);
        if (!animations.isEmpty()) {
            JsonObject animationTable = new JsonObject();
            animations.forEach(animationTable::addProperty);
            description.add("animations", animationTable);
        }
        if (!loops.isEmpty()) {
            JsonArray animate = new JsonArray();
            loops.forEach(animate::add);
            scripts.add("animate", animate);
        }
        description.add("scripts", scripts);
        JsonArray renderControllers = new JsonArray();
        renderControllers.add("controller.render.armor");
        description.add("render_controllers", renderControllers);

        JsonObject attachable = new JsonObject();
        attachable.add("description", description);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("minecraft:attachable", attachable);
        return root;
    }

    /**
     * Armor attachable backed by a generated vanilla-style sheet and the slot's built-in
     * armor geometry.
     */
    static @NotNull JsonObject attachable(@NotNull ArmorDefinition definition, @NotNull String textureName) {
        Key key = definition.key();
        ArmorSlot slot = definition.slot();

        JsonObject materials = new JsonObject();
        materials.addProperty("default", "armor");
        materials.addProperty("enchanted", "armor_enchanted");

        JsonObject textures = new JsonObject();
        textures.addProperty("default", texturePath(textureName, textureLayer(slot)));
        // The glint material needs a texture of its own. Omitting it leaves an enchanted
        // piece untextured rather than merely without the glint.
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
