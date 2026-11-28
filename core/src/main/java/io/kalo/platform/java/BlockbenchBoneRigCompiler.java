package io.kalo.platform.java;

import io.kalo.model.ModelIR;
import io.kalo.model.animation.ModelAnimations;
import io.kalo.pack.ResourcePack;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Splits an animated Blockbench model into one Java model per bone and publishes the rig.
 *
 * <p>Used by animated blocks/furniture and by placed items. The source bones keep their
 * authored pivots; the runtime applies the sampled pose around those pivots. Compilation is
 * all-or-nothing: one visible bone that Java cannot represent falls back to the static
 * single-display path instead of silently losing geometry.</p>
 */
final class BlockbenchBoneRigCompiler {

    private BlockbenchBoneRigCompiler() {
    }

    static @Nullable BlockbenchRigRegistry.Rig compile(
            @NotNull ResourcePack pack,
            @NotNull Key key,
            @NotNull ModelIR imported,
            @NotNull Map<String, Key> textureOverrides,
            @NotNull String stem,
            @NotNull String modelDirectory,
            @NotNull Logger logger,
            @NotNull String label) {
        List<ModelAnimations.Clip> allClips = ModelAnimations.all(imported);
        if (allClips.isEmpty()) {
            return null;
        }
        List<ModelAnimations.Clip> clips = allClips.stream()
                .filter(ModelAnimations.Clip::isLoop)
                .toList();
        List<BlockbenchRigRegistry.BoneRig> bones = new ArrayList<>();
        int index = 0;
        for (ModelIR.Bone bone : imported.bones()) {
            index++;
            if (!bone.visible() || !bone.export()) {
                continue;
            }
            boolean hasVisibleCube = bone.cubes().stream()
                    .anyMatch(cube -> cube.visible() && cube.export());
            if (!hasVisibleCube) {
                continue;
            }
            ModelIR singleBone = new ModelIR(
                    imported.id(), imported.name(), imported.formatVersion(),
                    imported.modelFormat(), imported.textureWidth(), imported.textureHeight(),
                    imported.textures(), List.of(bone), List.of(), List.of(),
                    imported.display(), List.of());
            String boneStem = stem + "_bone_" + index;
            io.kalo.model.java.JavaModelCompiler.Result compiled =
                    io.kalo.model.java.JavaModelCompiler.compile(singleBone,
                            textureOverrides, key.namespace(), boneStem);
            if (!compiled.compiled()) {
                logger.warning("Could not compile bone '" + bone.name() + "' of "
                        + label + " for Java animation: " + compiled.errorSummary()
                        + "; the model keeps its static display");
                return null;
            }
            for (io.kalo.model.ModelTextureResolver.ResolvedTexture texture
                    : compiled.textures()) {
                if (texture.embeddedPng() != null) {
                    pack.file("assets/" + texture.key().namespace() + "/textures/"
                                    + texture.key().value() + ".png",
                            io.kalo.pack.Writable.bytes(texture.embeddedPng()));
                }
            }
            Key boneModel = Key.key(key.namespace(),
                    modelDirectory + "/" + key.value() + "_bone_" + index);
            pack.file("assets/" + key.namespace() + "/models/" + modelDirectory + "/"
                    + key.value() + "_bone_" + index + ".json",
                    io.kalo.pack.Json.writable(compiled.model()));
            bones.add(new BlockbenchRigRegistry.BoneRig(bone.id(), bone.name(),
                    bone.parentId(), bone.pivot(), boneModel));
        }
        if (bones.isEmpty()) {
            return null;
        }
        return new BlockbenchRigRegistry.Rig(key, bones, clips, allClips,
                imported.controllers());
    }
}
