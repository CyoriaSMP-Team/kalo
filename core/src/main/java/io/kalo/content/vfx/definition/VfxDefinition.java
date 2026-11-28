package io.kalo.content.vfx.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * One playable VFX: a called name, its recipe and (when it came from a preset) the preset
 * id. The sprite model keys are deterministic so the pack compiler and runtime cannot
 * disagree about which texture a layer uses.
 */
public record VfxDefinition(
        @NotNull Key key,
        @NotNull String name,
        @NotNull VfxRecipe recipe,
        @Nullable String preset
) {

    public VfxDefinition {
        if (name.isBlank() || name.length() > 120
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("VFX name is invalid");
        }
        name = name.strip();
        preset = preset == null ? null : preset.trim().toLowerCase(Locale.ROOT);
    }

    /** Item model id for a motes layer, e.g. {@code mypack:vfx/frost_nova/0}. */
    public @NotNull Key spriteModel(int layerIndex) {
        if (layerIndex < 0 || layerIndex >= recipe.layers().size()) {
            throw new IllegalArgumentException("VFX layer index out of range: " + layerIndex);
        }
        return Key.key(key.namespace(), "vfx/" + key.value() + "/" + layerIndex);
    }

    /** Texture id for a motes layer, e.g. {@code mypack:vfx/frost_nova/0}. */
    public @NotNull Key spriteTexture(int layerIndex) {
        return spriteModel(layerIndex);
    }

    /** Item model id for a camera layer, e.g. {@code mypack:vfx/frost/camera_0}. */
    public @NotNull Key cameraSpriteModel(int cameraIndex) {
        if (cameraIndex < 0 || cameraIndex >= recipe.cameras().size()) {
            throw new IllegalArgumentException(
                    "VFX camera layer index out of range: " + cameraIndex);
        }
        return Key.key(key.namespace(),
                "vfx/" + key.value() + "/camera_" + cameraIndex);
    }

    /** Texture id for a camera layer; it shares the model's path. */
    public @NotNull Key cameraSpriteTexture(int cameraIndex) {
        return cameraSpriteModel(cameraIndex);
    }

    /**
     * Sprite model for an encoded layer index.
     *
     * <p>Motes layers keep their non-negative index. Camera layers are encoded as
     * {@code -1 - cameraIndex}, which keeps one sprite-id space in the runtime while
     * leaving {@link #spriteModel(int)} unchanged for existing callers.</p>
     */
    public @NotNull Key spriteModelFor(int encodedLayerIndex) {
        return encodedLayerIndex >= 0
                ? spriteModel(encodedLayerIndex)
                : cameraSpriteModel(-encodedLayerIndex - 1);
    }

    public @NotNull Key spriteTextureFor(int encodedLayerIndex) {
        return encodedLayerIndex >= 0
                ? spriteTexture(encodedLayerIndex)
                : cameraSpriteTexture(-encodedLayerIndex - 1);
    }

    /** Encodes a camera layer index into the shared sprite-id space. */
    public static int cameraLayerId(int cameraIndex) {
        return -1 - cameraIndex;
    }

    public static boolean isCameraLayer(int encodedLayerIndex) {
        return encodedLayerIndex < 0;
    }

    public int layerCount() {
        return recipe.layers().size();
    }

    public int cameraLayerCount() {
        return recipe.cameras().size();
    }
}
