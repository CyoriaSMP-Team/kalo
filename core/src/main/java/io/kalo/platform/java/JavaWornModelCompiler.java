package io.kalo.platform.java;

import io.kalo.content.armor.Armor;
import io.kalo.content.armor.ArmorDefinition;
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
 * Compiles a Blockbench armor model into a Java model the worn-armor display can render.
 *
 * <p>Java's client cannot attach a model to a player bone, so the runtime puts the model
 * on a display entity at the wearer's feet. The model must be authored in the Blockbench
 * player-template frame for that to line up; nothing is re-centred here.</p>
 */
public final class JavaWornModelCompiler {

    private static final Logger LOGGER = Logger.getLogger(JavaWornModelCompiler.class.getName());

    private JavaWornModelCompiler() {
    }

    public static void compile(@NotNull ResourcePack pack, @NotNull Iterable<Armor> armors) {
        for (Armor armor : armors) {
            ArmorDefinition definition = armor.armorDefinition();
            ArmorDefinition.ArmorModel worn = definition.armorModel();
            if (worn == null) {
                continue;
            }
            Key key = definition.key();
            try {
                ModelIR model = ModelRegistry.load(pack, worn.source());
                String stem = key.namespace() + "_" + key.value() + "_worn";
                JavaModelCompiler.Result compiled = JavaModelCompiler.compile(model,
                        worn.textures(), key.namespace(), stem);
                io.kalo.model.ModelDiagnostics.log(LOGGER,
                        "Blockbench worn armor model " + key.asString(), compiled.diagnostics());
                if (!compiled.compiled()) {
                    LOGGER.warning("Worn armor model for " + key.asString()
                            + " did not compile for Java: " + compiled.errorSummary());
                    continue;
                }
                for (ModelTextureResolver.ResolvedTexture texture : compiled.textures()) {
                    if (texture.embeddedPng() != null) {
                        pack.file(texturePath(texture.key()),
                                Writable.bytes(texture.embeddedPng()));
                    }
                }
                Key modelKey = Key.key(key.namespace(), "armor/" + key.value());
                pack.file("assets/" + key.namespace() + "/models/armor/" + key.value() + ".json",
                        Json.writable(compiled.model()));
                WornModelRegistry.put(new WornModelRegistry.Piece(key, modelKey,
                        ModelAnimations.loops(model), worn.animation()));
                BlockbenchRigRegistry.Rig rig = BlockbenchBoneRigCompiler.compile(pack,
                        key, model, worn.textures(), stem, "armor", LOGGER,
                        "worn armor " + key.asString());
                if (rig != null) {
                    BlockbenchRigRegistry.putWorn(rig);
                }
            } catch (Exception error) {
                LOGGER.log(Level.WARNING, "Could not compile worn armor model for "
                        + key.asString(), error);
            }
        }
    }

    private static @NotNull String texturePath(@NotNull Key texture) {
        return "assets/" + texture.namespace() + "/textures/" + texture.value() + ".png";
    }
}
