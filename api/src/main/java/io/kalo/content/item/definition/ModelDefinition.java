package io.kalo.content.item.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

/**
 * How a piece of content looks, described as authoring intent rather than as any one
 * platform's output format.
 *
 * <p>The Java compiler turns a {@link Sprite} into an item definition plus a generated
 * {@code item/generated} model; a Bedrock compiler would turn the same {@link Sprite}
 * into an {@code item_texture.json} entry and a Geyser mapping, with no model file at
 * all. Neither decision belongs here.</p>
 */
public sealed interface ModelDefinition {

    /** A flat sprite generated from a single texture — the common case. */
    record Sprite(@NotNull Key texture) implements ModelDefinition {
    }

    /** A model authored externally (Blockbench) and shipped inside the pack. */
    record Custom(@NotNull Key model, @NotNull @Unmodifiable Map<String, Key> textures) implements ModelDefinition {
        public Custom {
            textures = Map.copyOf(textures);
        }
    }

    /**
     * A Blockbench {@code .bbmodel} source imported into Kalo's shared model IR.
     *
     * <p>The Java compiler emits a Java model from the IR; the Bedrock compiler emits
     * geometry with the original outliner hierarchy. Neither compiler copies Blockbench's
     * project JSON into the pack as if it were a client model.</p>
     *
     * @param source   pack-relative source under {@code assets/<namespace>/}, e.g.
     *                 {@code testpack:models/item/ruby_sword.bbmodel}
     * @param textures optional Kalo texture overrides keyed by Blockbench texture id,
     *                 name or path
     */
    record Blockbench(@NotNull Key source, @NotNull @Unmodifiable Map<String, Key> textures) implements ModelDefinition {
        public Blockbench {
            textures = Map.copyOf(textures);
        }
    }

    /**
     * Reuse of a vanilla item's own appearance.
     *
     * @param item the vanilla item key, e.g. {@code minecraft:apple} — not a model path
     */
    record Vanilla(@NotNull Key item) implements ModelDefinition {
    }
}
