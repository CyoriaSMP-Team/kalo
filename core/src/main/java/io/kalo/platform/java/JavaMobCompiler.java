package io.kalo.platform.java;

import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.content.mob.Mob;
import io.kalo.content.mob.MobDefinition;
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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Compiles a custom mob's Blockbench model into a Java model for its display rig.
 *
 * <p>Mob models are authored in Bedrock entity space (feet at y=0, X/Z centred). The Java
 * display adds the half-block correction the item renderer needs; nothing is baked into
 * the model.</p>
 */
public final class JavaMobCompiler {

    private static final Logger LOGGER = Logger.getLogger(JavaMobCompiler.class.getName());

    private JavaMobCompiler() {
    }

    public static void compile(@NotNull ResourcePack pack, @NotNull Iterable<Mob> mobs) {
        for (Mob mob : mobs) {
            MobDefinition definition = mob.definition();
            if (!(definition.model() instanceof ModelDefinition.Blockbench blockbench)) {
                continue;
            }
            Key key = definition.key();
            try {
                ModelIR model = ModelRegistry.load(pack, blockbench.source());
                String stem = key.namespace() + "_" + key.value();
                JavaModelCompiler.Result compiled = JavaModelCompiler.compile(model,
                        blockbench.textures(), key.namespace(), stem + "_mob");
                io.kalo.model.ModelDiagnostics.log(LOGGER,
                        "Blockbench mob model " + key.asString(), compiled.diagnostics());
                if (!compiled.compiled()) {
                    LOGGER.warning("Mob model for " + key.asString()
                            + " did not compile for Java: " + compiled.errorSummary()
                            + "; the base mob stays visible");
                    continue;
                }
                for (ModelTextureResolver.ResolvedTexture texture : compiled.textures()) {
                    if (texture.embeddedPng() != null) {
                        pack.file(texturePath(texture.key()),
                                Writable.bytes(texture.embeddedPng()));
                    }
                }
                Key modelKey = Key.key(key.namespace(), "mob/" + key.value());
                pack.file("assets/" + key.namespace() + "/models/mob/" + key.value()
                        + ".json", Json.writable(compiled.model()));
                MobModelRegistry.put(new MobModelRegistry.Piece(key, modelKey,
                        definition.height(), ModelAnimations.loops(model)));
                BlockbenchRigRegistry.Rig rig = BlockbenchBoneRigCompiler.compile(pack,
                        key, model, blockbench.textures(), stem, "mob", LOGGER,
                        "mob " + key.asString());
                if (rig != null) {
                    BlockbenchRigRegistry.putMob(rig);
                }
            } catch (Exception error) {
                LOGGER.log(Level.WARNING, "Could not compile mob model for "
                        + key.asString(), error);
            }
        }
    }

    private static @NotNull String texturePath(@NotNull Key texture) {
        return "assets/" + texture.namespace() + "/textures/" + texture.value() + ".png";
    }
}
