package io.kalo.content.block.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Set;

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
        private static final Set<String> ALLOWED_FACES = Set.of(
                "all", "particle", "down", "bottom", "up", "top",
                "north", "south", "west", "east");

        public Cube {
            faces = Map.copyOf(faces);
            if (faces.isEmpty()) {
                throw new IllegalArgumentException("a cube needs at least one texture");
            }
            for (String face : faces.keySet()) {
                if (!ALLOWED_FACES.contains(face)) {
                    throw new IllegalArgumentException("unknown cube face '" + face + "'");
                }
            }
            if (faces.containsKey("up") && faces.containsKey("top")) {
                throw new IllegalArgumentException("cube cannot declare both 'up' and its alias 'top'");
            }
            if (faces.containsKey("down") && faces.containsKey("bottom")) {
                throw new IllegalArgumentException("cube cannot declare both 'down' and its alias 'bottom'");
            }
            if (!faces.containsKey("all")) {
                boolean complete = (faces.containsKey("up") || faces.containsKey("top"))
                        && (faces.containsKey("down") || faces.containsKey("bottom"))
                        && faces.containsKey("north") && faces.containsKey("south")
                        && faces.containsKey("west") && faces.containsKey("east");
                if (!complete) {
                    throw new IllegalArgumentException(
                            "cube needs an 'all' fallback or a texture for every face");
                }
            }
        }
    }

    /** A model authored externally and shipped inside the pack. */
    record Custom(@NotNull Key model, @NotNull @Unmodifiable Map<String, Key> textures) implements BlockModelDefinition {
        public Custom {
            textures = Map.copyOf(textures);
        }
    }
}
