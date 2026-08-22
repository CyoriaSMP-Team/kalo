package io.kalo.content.furniture.definition;

import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.DisplayProperties;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.content.block.definition.JavaBlockOptions;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;

/**
 * Platform-neutral description of a furniture piece.
 *
 * <p>Extends block concepts with furniture-specific properties: rotation, seating,
 * hitboxes, and display transforms. Like {@link BlockDefinition}, nothing here names
 * a platform concept — each compiler makes its own platform decisions.</p>
 */
public record FurnitureDefinition(
        @NotNull Key key,
        @NotNull DisplayProperties display,
        @NotNull BlockModelDefinition model,
        @NotNull FurnitureBehaviour behaviour,
        @NotNull JavaBlockOptions java,
        @NotNull BedrockOptions bedrock,
        @NotNull FurnitureDisplay displayTransform
) {

    /**
     * Converts this furniture definition to a block definition for backward compatibility.
     *
     * <p>The block definition contains the shared properties (key, display, model,
     * behaviour hardness/requiresTool, java, bedrock) that both blocks and furniture use.</p>
     */
    public @NotNull BlockDefinition toBlockDefinition() {
        return BlockDefinition.builder(key)
                .display(display)
                .model(model)
                .behaviour(new io.kalo.content.block.definition.BlockBehaviour(
                        behaviour.hardness(), behaviour.requiresTool()))
                .java(java)
                .bedrock(bedrock)
                .build();
    }

    public static @NotNull Builder builder(@NotNull Key key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final Key key;
        private DisplayProperties display = DisplayProperties.empty();
        private BlockModelDefinition model;
        private FurnitureBehaviour behaviour = FurnitureBehaviour.defaults();
        private JavaBlockOptions java = JavaBlockOptions.defaults();
        private BedrockOptions bedrock = BedrockOptions.defaults();
        private FurnitureDisplay displayTransform = FurnitureDisplay.defaults();

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

        public @NotNull Builder behaviour(@NotNull FurnitureBehaviour behaviour) {
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

        public @NotNull Builder displayTransform(@NotNull FurnitureDisplay displayTransform) {
            this.displayTransform = displayTransform;
            return this;
        }

        public @NotNull FurnitureDefinition build() {
            if (model == null) {
                throw new IllegalStateException(
                        "furniture " + key.asString() + " has no model; declare 'cube_all', 'cube' or 'custom'");
            }
            return new FurnitureDefinition(key, display, model, behaviour, java, bedrock, displayTransform);
        }
    }
}
