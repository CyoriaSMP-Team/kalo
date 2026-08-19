package io.kalo.content.block.definition;

import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.DisplayProperties;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * The platform-neutral description of a custom block.
 *
 * <p>Same contract as {@link io.kalo.content.item.definition.ItemDefinition}: nothing in
 * {@link #display()}, {@link #model()} or {@link #behaviour()} names a platform concept.
 * The Java compiler chooses either a borrowed vanilla state or a virtual anchor/display
 * backend; a Bedrock compiler registers a real custom block through Geyser and borrows
 * nothing.</p>
 */
public record BlockDefinition(
        @NotNull Key key,
        @NotNull DisplayProperties display,
        @NotNull BlockModelDefinition model,
        @NotNull BlockBehaviour behaviour,
        @NotNull JavaBlockOptions java,
        @NotNull BedrockOptions bedrock
) {

    /** Translation key for the block's item form. */
    public @NotNull String translationKey() {
        return "block." + key.namespace() + "." + key.value();
    }

    public static @NotNull Builder builder(@NotNull Key key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final Key key;
        private DisplayProperties display = DisplayProperties.empty();
        private BlockModelDefinition model;
        private BlockBehaviour behaviour = BlockBehaviour.defaults();
        private JavaBlockOptions java = JavaBlockOptions.defaults();
        private BedrockOptions bedrock = BedrockOptions.defaults();

        private Builder(@NotNull Key key) {
            this.key = key;
        }

        public @NotNull Builder display(@NotNull DisplayProperties display) {
            this.display = display;
            return this;
        }

        public @NotNull Builder model(@NotNull BlockModelDefinition model) {
            this.model = model;
            return this;
        }

        public @NotNull Builder behaviour(@NotNull BlockBehaviour behaviour) {
            this.behaviour = behaviour;
            return this;
        }

        public @NotNull Builder java(@NotNull JavaBlockOptions java) {
            this.java = java;
            return this;
        }

        public @NotNull Builder bedrock(@NotNull BedrockOptions bedrock) {
            this.bedrock = bedrock;
            return this;
        }

        public @NotNull BlockDefinition build() {
            if (model == null) {
                // Unlike items there is no sensible vanilla fallback: a block with no
                // model would render as missing texture in the world, which is worse
                // than refusing to load it with a message.
                throw new IllegalStateException(
                        "block " + key.asString() + " has no model; declare 'cube_all', 'cube' or 'custom'");
            }
            return new BlockDefinition(key, display, model, behaviour, java, bedrock);
        }
    }
}
