package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.content.vfx.definition.VfxDefinition;
import io.kalo.content.vfx.definition.VfxRecipe;
import io.kalo.model.ModelIR;
import io.kalo.model.ModelRegistry;
import io.kalo.model.ModelTextureResolver;
import io.kalo.model.bedrock.BedrockModelCompiler;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes Bedrock client-entity assets for VFX layers and records their identifiers.
 *
 * <p>Motes and camera layers become thin textured cubes. Model layers reuse the same
 * conversion paths as custom items: a Blockbench source keeps its bones, a Java item
 * model is converted to cuboids (with an atlas when it spans sheets), and a sprite becomes
 * a flat plane. The runtime then spawns/moves/removes the registered client entity through
 * the existing VFX rig seam.</p>
 */
public final class BedrockVfxCompiler {

    private static final Logger LOGGER = Logger.getLogger(BedrockVfxCompiler.class.getName());
    private static final int TEXTURE_SIZE = 32;

    private BedrockVfxCompiler() {
    }

    public static @NotNull List<BedrockRigRegistration> compile(
            @NotNull ResourcePack javaSource, @NotNull ResourcePack target,
            @NotNull Iterable<VfxDefinition> definitions) {
        return compile(javaSource, target, definitions, key -> null);
    }

    public static @NotNull List<BedrockRigRegistration> compile(
            @NotNull ResourcePack javaSource, @NotNull ResourcePack target,
            @NotNull Iterable<VfxDefinition> definitions,
            @NotNull Function<Key, ModelDefinition> modelResolver) {
        List<BedrockRigRegistration> registrations = new ArrayList<>();
        for (VfxDefinition definition : definitions) {
            for (int layer = 0; layer < definition.layerCount(); layer++) {
                add(definition, layer, definition.recipe().layers().get(layer).size(),
                        javaSource, target, registrations);
            }
            for (int camera = 0; camera < definition.cameraLayerCount(); camera++) {
                int encoded = VfxDefinition.cameraLayerId(camera);
                add(definition, encoded,
                        definition.recipe().cameras().get(camera).layer().size(),
                        javaSource, target, registrations);
            }
            for (int model = 0; model < definition.recipe().models().size(); model++) {
                addModel(definition, model, definition.recipe().models().get(model),
                        javaSource, target, modelResolver, registrations);
            }
        }
        return List.copyOf(registrations);
    }

    private static void add(@NotNull VfxDefinition definition, int encodedLayer,
                            double size, @NotNull ResourcePack javaSource,
                            @NotNull ResourcePack target,
                            @NotNull List<BedrockRigRegistration> registrations) {
        String suffix = encodedLayer >= 0 ? Integer.toString(encodedLayer)
                : "camera_" + (-encodedLayer - 1);
        String shorthand = sanitize(definition.key().namespace()
                + "_" + definition.key().value() + "_vfx_" + suffix);
        String texturePath = "textures/entity/vfx/" + shorthand;
        Writable texture = javaSource.file(
                io.kalo.platform.java.VfxSpritePainter.texturePath(definition, encodedLayer));
        if (texture == null) {
            return;
        }
        target.file(texturePath + ".png", texture);
        String geometryId = "geometry.kalo.vfx_" + shorthand;
        target.file("models/entity/" + shorthand + ".geo.json",
                Json.writable(geometry(geometryId, size)));
        String entityId = "kalo:vfx_" + shorthand;
        target.file("entity/" + shorthand + ".entity.json",
                Json.writable(BedrockPackCompiler.placedEntity(entityId, geometryId,
                        texturePath, Map.of(), Map.of(), List.of())));
        BedrockVfxRegistry.put(definition.key(), encodedLayer, entityId);
        registrations.add(new BedrockRigRegistration(definition.key(), entityId));
    }

    private static void addModel(@NotNull VfxDefinition definition, int index,
                                 @NotNull VfxRecipe.Model layer,
                                 @NotNull ResourcePack javaSource,
                                 @NotNull ResourcePack target,
                                 @NotNull Function<Key, ModelDefinition> modelResolver,
                                 @NotNull List<BedrockRigRegistration> registrations) {
        String shorthand = sanitize(definition.key().namespace()
                + "_" + definition.key().value() + "_vfx_model_" + index);
        String entityId = "kalo:vfx_" + shorthand;
        String geometryId = "geometry.kalo.vfx_" + shorthand;
        try {
            ModelDefinition resolved = modelResolver.apply(layer.model());
            boolean compiled;
            if (resolved instanceof ModelDefinition.Blockbench blockbench) {
                compiled = addBlockbenchModel(blockbench, shorthand, entityId, geometryId,
                        layer.roll(), javaSource, target);
            } else if (resolved instanceof ModelDefinition.Custom custom) {
                compiled = addJavaModel(custom.model(), custom.textures(), shorthand,
                        entityId, geometryId, layer.roll(), javaSource, target);
            } else if (resolved instanceof ModelDefinition.Sprite sprite) {
                compiled = addSpriteModel(sprite.texture(), shorthand, entityId,
                        geometryId, layer.roll(), javaSource, target);
            } else {
                // A key that names a pack model directly, or a vanilla model that the
                // pack does not carry. The latter resolves to null and is skipped.
                compiled = addJavaModel(layer.model(), Map.of(), shorthand, entityId,
                        geometryId, layer.roll(), javaSource, target);
            }
            if (compiled) {
                BedrockVfxRegistry.putModel(definition.key(), index, entityId);
                registrations.add(new BedrockRigRegistration(definition.key(), entityId));
            }
        } catch (Exception error) {
            LOGGER.log(Level.WARNING, "Cannot convert Bedrock VFX model "
                    + definition.key() + " model " + index + ": " + error.getMessage(),
                    error);
        }
    }

    private static boolean addBlockbenchModel(@NotNull ModelDefinition.Blockbench blockbench,
                                              @NotNull String shorthand,
                                              @NotNull String entityId,
                                              @NotNull String geometryId,
                                              double roll,
                                              @NotNull ResourcePack javaSource,
                                              @NotNull ResourcePack target) throws Exception {
        ModelIR model = ModelRegistry.load(javaSource, blockbench.source());
        BedrockModelCompiler.Result compiled = BedrockModelCompiler.compile(model,
                geometryId, blockbench.textures(), blockbench.source().namespace(), shorthand);
        if (!compiled.compiled()) {
            LOGGER.warning("VFX model " + blockbench.source()
                    + " did not compile for Bedrock: " + compiled.errorSummary());
            return false;
        }
        List<ModelTextureResolver.ResolvedTexture> textures = compiled.textures();
        for (ModelTextureResolver.ResolvedTexture texture : textures) {
            if (texture.embeddedPng() == null
                    && javaSource.file(texturePath(texture.key())) == null) {
                LOGGER.warning("VFX model " + blockbench.source()
                        + " is missing texture " + texture.key());
                return false;
            }
        }
        Map<String, String> materials = new TreeMap<>();
        for (ModelTextureResolver.ResolvedTexture texture : textures) {
            String destination = "textures/entity/vfx/" + shorthand + "_"
                    + texture.material();
            materials.put(texture.material(), destination);
            if (texture.embeddedPng() != null) {
                target.file(destination + ".png", Writable.bytes(texture.embeddedPng()));
            } else {
                target.file(destination + ".png",
                        javaSource.file(texturePath(texture.key())));
            }
        }
        if (materials.isEmpty()) {
            return false;
        }
        JsonObject geometry = compiled.geometry();
        applyRoll(geometry, roll);
        writeModel(target, shorthand, entityId, geometryId,
                geometry, materials.values().iterator().next(), materials);
        return true;
    }

    private static boolean addJavaModel(@NotNull Key modelKey,
                                        @NotNull Map<String, Key> overrides,
                                        @NotNull String shorthand,
                                        @NotNull String entityId,
                                        @NotNull String geometryId,
                                        double roll,
                                        @NotNull ResourcePack javaSource,
                                        @NotNull ResourcePack target) throws Exception {
        JsonObject javaModel = BedrockItemModelResolver.resolve(javaSource, modelKey);
        if (javaModel == null) {
            return false;
        }
        JsonObject geometry = BedrockGeometry.convert(geometryId, javaModel);
        if (geometry == null) {
            return false;
        }
        ModelDefinition.Custom custom = new ModelDefinition.Custom(modelKey, overrides);
        String texturePath = "textures/entity/vfx/" + shorthand;
        Key single = BedrockPackCompiler.singleSheet(custom, javaModel);
        if (single != null) {
            Writable png = javaSource.file(texturePath(single));
            if (png == null) {
                return false;
            }
            target.file(texturePath + ".png", png);
        } else {
            BedrockItemAtlas.Result atlas = BedrockItemAtlas.build(custom, javaModel,
                    geometry, javaSource);
            if (atlas == null) {
                return false;
            }
            target.file(texturePath + ".png", Writable.bytes(atlas.image()));
        }
        normalizeMaterials(geometry);
        applyRoll(geometry, roll);
        writeModel(target, shorthand, entityId, geometryId, geometry, texturePath, Map.of());
        return true;
    }

    private static boolean addSpriteModel(@NotNull Key texture,
                                           @NotNull String shorthand,
                                           @NotNull String entityId,
                                           @NotNull String geometryId,
                                           double roll,
                                           @NotNull ResourcePack javaSource,
                                           @NotNull ResourcePack target) {
        Writable png = javaSource.file(texturePath(texture));
        if (png == null) {
            return false;
        }
        String texturePath = "textures/entity/vfx/" + shorthand;
        target.file(texturePath + ".png", png);
        JsonObject geometry = geometry(geometryId, 1.0);
        applyRoll(geometry, roll);
        writeModel(target, shorthand, entityId, geometryId, geometry, texturePath, Map.of());
        return true;
    }

    /**
     * Bedrock's entity packet carries yaw and pitch only. A model layer's authored roll is
     * static, so bake it into the geometry as a wrapper bone rotation instead of dropping
     * it. Java applies the same angle to the item display's Z rotation.
     */
    private static void applyRoll(@NotNull JsonObject geometry, double roll) {
        if (Math.abs(roll) < 1e-6) {
            return;
        }
        JsonObject shape = geometry.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject();
        JsonArray bones = shape.getAsJsonArray("bones");
        if (bones == null || bones.isEmpty()) {
            return;
        }
        String wrapper = "kalo_vfx_roll";
        boolean collides = true;
        while (collides) {
            collides = false;
            for (JsonElement element : bones) {
                JsonObject bone = element.getAsJsonObject();
                if (bone.has("name") && bone.get("name").getAsString().equals(wrapper)) {
                    collides = true;
                    wrapper += "_";
                    break;
                }
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("name", wrapper);
        JsonArray pivot = new JsonArray();
        pivot.add(0);
        pivot.add(0);
        pivot.add(0);
        root.add("pivot", pivot);
        JsonArray rotation = new JsonArray();
        rotation.add(0);
        rotation.add(0);
        rotation.add(roll);
        root.add("rotation", rotation);

        JsonArray reordered = new JsonArray();
        reordered.add(root);
        boolean parented = false;
        for (JsonElement element : bones) {
            JsonObject bone = element.getAsJsonObject();
            if (!bone.has("parent")) {
                bone.addProperty("parent", wrapper);
                parented = true;
            }
            reordered.add(bone);
        }
        if (parented) {
            shape.add("bones", reordered);
        }
    }

    private static void writeModel(@NotNull ResourcePack target, @NotNull String shorthand,
                                   @NotNull String entityId, @NotNull String geometryId,
                                   @NotNull JsonObject geometry, @NotNull String texture,
                                   @NotNull Map<String, String> materials) {
        target.file("models/entity/" + shorthand + ".geo.json", Json.writable(geometry));
        target.file("entity/" + shorthand + ".entity.json",
                Json.writable(BedrockPackCompiler.placedEntity(entityId, geometryId,
                        texture, materials, Map.of(), List.of())));
    }

    /** Java models can name a texture per face; the client-entity texture map is single. */
    private static void normalizeMaterials(@NotNull JsonObject geometry) {
        for (JsonElement shapeElement : geometry.getAsJsonArray("minecraft:geometry")) {
            JsonObject shape = shapeElement.getAsJsonObject();
            for (JsonElement boneElement : shape.getAsJsonArray("bones")) {
                JsonArray cubes = boneElement.getAsJsonObject().getAsJsonArray("cubes");
                if (cubes == null) {
                    continue;
                }
                for (JsonElement cubeElement : cubes) {
                    JsonElement uv = cubeElement.getAsJsonObject().get("uv");
                    if (uv == null || !uv.isJsonObject()) {
                        continue;
                    }
                    for (var face : uv.getAsJsonObject().entrySet()) {
                        if (face.getValue().isJsonObject()) {
                            face.getValue().getAsJsonObject()
                                    .addProperty("material_instance", "default");
                        }
                    }
                }
            }
        }
    }

    private static @NotNull String texturePath(@NotNull Key texture) {
        return "assets/" + texture.namespace() + "/textures/" + texture.value() + ".png";
    }

    private static @NotNull JsonObject geometry(@NotNull String identifier, double size) {
        double pixels = Math.max(1.0, Math.min(128.0, size * 16.0));
        double half = pixels / 2.0;

        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        description.addProperty("texture_width", TEXTURE_SIZE);
        description.addProperty("texture_height", TEXTURE_SIZE);
        description.addProperty("visible_bounds_width", 2);
        description.addProperty("visible_bounds_height", 2);
        JsonArray offset = new JsonArray();
        offset.add(0);
        offset.add(1);
        offset.add(0);
        description.add("visible_bounds_offset", offset);

        JsonObject cube = new JsonObject();
        JsonArray origin = new JsonArray();
        origin.add(-half);
        origin.add(-half);
        origin.add(-0.5);
        cube.add("origin", origin);
        JsonArray dimensions = new JsonArray();
        dimensions.add(pixels);
        dimensions.add(pixels);
        dimensions.add(1);
        cube.add("size", dimensions);
        cube.add("uv", fullTextureUv());

        JsonObject bone = new JsonObject();
        bone.addProperty("name", "sprite");
        JsonArray pivot = new JsonArray();
        pivot.add(0);
        pivot.add(0);
        pivot.add(0);
        bone.add("pivot", pivot);
        JsonArray cubes = new JsonArray();
        cubes.add(cube);
        bone.add("cubes", cubes);

        JsonObject geometry = new JsonObject();
        geometry.add("description", description);
        JsonArray bones = new JsonArray();
        bones.add(bone);
        geometry.add("bones", bones);

        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.12.0");
        JsonArray geometries = new JsonArray();
        geometries.add(geometry);
        root.add("minecraft:geometry", geometries);
        return root;
    }

    private static @NotNull JsonObject fullTextureUv() {
        JsonObject uv = new JsonObject();
        for (String face : List.of("north", "south", "east", "west", "up", "down")) {
            JsonObject mapping = new JsonObject();
            JsonArray origin = new JsonArray();
            origin.add(0);
            origin.add(0);
            mapping.add("uv", origin);
            JsonArray size = new JsonArray();
            size.add(TEXTURE_SIZE);
            size.add(TEXTURE_SIZE);
            mapping.add("uv_size", size);
            uv.add(face, mapping);
        }
        return uv;
    }

    private static @NotNull String sanitize(@NotNull String value) {
        String cleaned = value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "vfx" : cleaned;
    }
}
