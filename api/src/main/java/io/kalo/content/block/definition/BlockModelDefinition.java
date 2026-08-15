package io.kalo.content.block.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

/**
 * How a custom block looks.
 *
 * <p>Kept separate from {@link io.kalo.content.item.definition.ModelDefinition} because
 * blocks and items are modelled differently on every platform — an item sprite is
 * generated from {@code item/generated}, a block is a cube. Sharing one type would force
 * both compilers to reject half its cases.</p>
 */
public sealed interface BlockModelDefinition {

    /** A cube with the same texture on all six faces — the common case. */
    record CubeAll(@NotNull Key texture) implements BlockModelDefinition {
    }

    /** A cube with a per-face texture. Missing faces fall back to {@code all}. */
    record Cube(@NotNull @Unmodifiable Map<String, Key> faces) implements BlockModelDefinition {
        public Cube {
            faces = Map.copyOf(faces);
        }
    }

    /** A model authored externally and shipped inside the pack. */
    record Custom(@NotNull Key model, @NotNull @Unmodifiable Map<String, Key> textures) implements BlockModelDefinition {
        public Custom {
            textures = Map.copyOf(textures);
        }
    }
}
