package io.kalo.platform.java;

import io.kalo.content.emote.Emote;
import io.kalo.content.emote.EmoteDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.content.vehicle.Vehicle;
import io.kalo.content.vehicle.VehicleDefinition;
import io.kalo.model.ModelIR;
import io.kalo.model.ModelRegistry;
import io.kalo.model.ModelTextureResolver;
import io.kalo.model.animation.ModelAnimations;
import io.kalo.model.java.JavaModelCompiler;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Compiles vehicle and emote prop models into Java models for their display rigs.
 *
 * <p>Both are authored in Bedrock entity space (feet/origin at y=0, X/Z centred), so the
 * runtime supplies the half-block correction the item renderer needs.</p>
 */
public final class JavaRigCompiler {

    private static final Logger LOGGER = Logger.getLogger(JavaRigCompiler.class.getName());

    private JavaRigCompiler() {
    }

    public static void compileEmotes(@NotNull ResourcePack pack,
                                     @NotNull Iterable<Emote> emotes) {
        for (Emote emote : emotes) {
            EmoteDefinition definition = emote.definition();
            ModelDefinition.Blockbench model = withSkin(definition);
            compile(pack, definition.key(), model, definition.scale(),
                    definition.yOffset(), RigModelRegistry.Kind.EMOTE);
            if (model != null && definition.rig() == EmoteDefinition.RigMode.PLAYER) {
                try {
                    ModelIR imported = ModelRegistry.load(pack, model.source());
                    Map<String, Key> textures = perPlayerTextures(pack, definition,
                            imported, model);
                    BlockbenchRigRegistry.Rig rig = BlockbenchBoneRigCompiler.compile(pack,
                            definition.key(), imported,
                            textures == null ? model.textures() : textures,
                            definition.key().namespace() + "_" + definition.key().value()
                                    + "_emote", "emote", LOGGER,
                            "emote " + definition.key().asString());
                    if (rig != null) {
                        BlockbenchRigRegistry.putEmote(rig);
                    }
                } catch (Exception error) {
                    LOGGER.log(Level.WARNING, "Could not compile player emote rig for "
                            + definition.key().asString(), error);
                }
            }
        }
    }

    private static ModelDefinition.@org.jetbrains.annotations.Nullable Blockbench withSkin(
            @NotNull EmoteDefinition definition) {
        if (!(definition.model() instanceof ModelDefinition.Blockbench blockbench)) {
            return null;
        }
        if (definition.skin() == null) {
            return blockbench;
        }
        java.util.Map<String, Key> textures = new java.util.LinkedHashMap<>(
                blockbench.textures());
        textures.putIfAbsent("0", definition.skin());
        return new ModelDefinition.Blockbench(blockbench.source(), textures);
    }

    /**
     * Copies the base skin to a stable, emote-unique path and points the body rig at it.
     * The per-player Java overlay replaces exactly this path, so the authored texture and
     * every other model sharing it stay untouched.
     */
    private static @Nullable Map<String, Key> perPlayerTextures(
            @NotNull ResourcePack pack, @NotNull EmoteDefinition definition,
            @NotNull ModelIR model, @NotNull ModelDefinition.Blockbench blockbench) {
        byte[] base = baseTexture(pack, definition, model, blockbench);
        if (base == null) {
            LOGGER.warning("Cannot enable per-player Java skin for "
                    + definition.key().asString()
                    + ": no base texture was found; the authored texture stays in use");
            return null;
        }
        Key unique = PlayerSkinCache.javaSkinKey(definition.key());
        pack.file("assets/" + unique.namespace() + "/textures/" + unique.value() + ".png",
                Writable.bytes(base));
        Map<String, Key> textures = new LinkedHashMap<>(blockbench.textures());
        if (!model.textures().isEmpty()) {
            ModelIR.Texture first = model.textures().get(0);
            textures.put(first.id(), unique);
            textures.put(first.name(), unique);
        }
        textures.put("0", unique);
        return textures;
    }

    private static byte @Nullable [] baseTexture(@NotNull ResourcePack pack,
                                                 @NotNull EmoteDefinition definition,
                                                 @NotNull ModelIR model,
                                                 @NotNull ModelDefinition.Blockbench blockbench) {
        if (definition.skin() != null) {
            byte[] skin = texture(pack, definition.skin());
            if (skin != null) {
                return skin;
            }
        }
        for (Key key : blockbench.textures().values()) {
            byte[] bytes = texture(pack, key);
            if (bytes != null) {
                return bytes;
            }
        }
        for (ModelIR.Texture texture : model.textures()) {
            if (texture.embeddedPng() != null) {
                return texture.embeddedPng();
            }
            if (texture.relativePath() != null) {
                Key key = keyFromRelativePath(texture.relativePath(),
                        definition.key().namespace());
                byte[] bytes = key == null ? null : texture(pack, key);
                if (bytes != null) {
                    return bytes;
                }
            }
        }
        return null;
    }

    private static byte @Nullable [] texture(@NotNull ResourcePack pack,
                                             @NotNull Key key) {
        Writable content = pack.file("assets/" + key.namespace() + "/textures/"
                + key.value() + ".png");
        if (content == null) {
            return null;
        }
        try {
            return content.toByteArray();
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static @Nullable Key keyFromRelativePath(@NotNull String relativePath,
                                                     @NotNull String fallbackNamespace) {
        String path = relativePath.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        String namespace = fallbackNamespace;
        if (path.startsWith("assets/")) {
            int separator = path.indexOf('/', "assets/".length());
            if (separator < 0) {
                return null;
            }
            namespace = path.substring("assets/".length(), separator);
            path = path.substring(separator + 1);
        }
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        int dot = path.lastIndexOf('.');
        if (dot > 0) {
            path = path.substring(0, dot);
        }
        return path.isBlank() ? null : Key.key(namespace, path);
    }

    public static void compileVehicles(@NotNull ResourcePack pack,
                                       @NotNull Iterable<Vehicle> vehicles) {
        for (Vehicle vehicle : vehicles) {
            VehicleDefinition definition = vehicle.definition();
            compile(pack, definition.key(), definition.model(), definition.scale(),
                    definition.yOffset(), RigModelRegistry.Kind.VEHICLE);
        }
    }

    private static void compile(@NotNull ResourcePack pack, @NotNull Key key,
                                @NotNull ModelDefinition modelDefinition,
                                double scale, double yOffset,
                                @NotNull RigModelRegistry.Kind kind) {
        if (!(modelDefinition instanceof ModelDefinition.Blockbench blockbench)) {
            return;
        }
        try {
            ModelIR model = ModelRegistry.load(pack, blockbench.source());
            String stem = key.namespace() + "_" + key.value() + "_" + kind.name()
                    .toLowerCase(java.util.Locale.ROOT);
            JavaModelCompiler.Result compiled = JavaModelCompiler.compile(model,
                    blockbench.textures(), key.namespace(), stem);
            io.kalo.model.ModelDiagnostics.log(LOGGER,
                    "Blockbench " + kind.name().toLowerCase(java.util.Locale.ROOT)
                            + " model " + key.asString(), compiled.diagnostics());
            if (!compiled.compiled()) {
                LOGGER.warning("Model for " + kind.name().toLowerCase(java.util.Locale.ROOT)
                        + " " + key.asString() + " did not compile for Java: "
                        + compiled.errorSummary());
                return;
            }
            for (ModelTextureResolver.ResolvedTexture texture : compiled.textures()) {
                if (texture.embeddedPng() != null) {
                    pack.file(texturePath(texture.key()), Writable.bytes(texture.embeddedPng()));
                }
            }
            String directory = kind == RigModelRegistry.Kind.VEHICLE ? "vehicle" : "emote";
            Key modelKey = Key.key(key.namespace(), directory + "/" + key.value());
            pack.file("assets/" + key.namespace() + "/models/" + directory + "/"
                    + key.value() + ".json", Json.writable(compiled.model()));
            RigModelRegistry.put(new RigModelRegistry.Piece(key, kind, modelKey, scale,
                    yOffset, ModelAnimations.loops(model)));
        } catch (Exception error) {
            LOGGER.log(Level.WARNING, "Could not compile " + kind.name().toLowerCase(
                    java.util.Locale.ROOT) + " model for " + key.asString(), error);
        }
    }

    private static @NotNull String texturePath(@NotNull Key texture) {
        return "assets/" + texture.namespace() + "/textures/" + texture.value() + ".png";
    }
}
