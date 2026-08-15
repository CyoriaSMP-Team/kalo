package io.kalo.content.item.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The platform-neutral description of a custom item.
 *
 * <p>This is the compilation source of truth: pack YAML is parsed into an
 * {@code ItemDefinition}, and each platform compiler consumes it independently to produce
 * its own output. Nothing in {@link #display()}, {@link #model()} or {@link #behaviour()}
 * names a platform concept; platform-specific choices live in {@link #java()} and
 * {@link #bedrock()}, where their presence is visible rather than implicit.</p>
 *
 * @see io.kalo.content.item.Item
 */
public record ItemDefinition(
        @NotNull Key key,
        @NotNull DisplayProperties display,
        @NotNull ModelDefinition model,
        @NotNull ItemBehaviour behaviour,
        @NotNull JavaOptions java,
        @NotNull BedrockOptions bedrock
) {

    /** The generated translation key, used when {@link DisplayProperties#name()} is null. */
    public @NotNull String translationKey() {
        return "item." + key.namespace() + "." + key.value();
    }

    public static @NotNull Builder builder(@NotNull Key key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final Key key;
        private DisplayProperties display = DisplayProperties.empty();
        private ModelDefinition model;
        private ItemBehaviour behaviour = ItemBehaviour.defaults();
        private JavaOptions java = JavaOptions.defaults();
        private BedrockOptions bedrock = BedrockOptions.defaults();

        private Builder(@NotNull Key key) {
            this.key = key;
        }

        public @NotNull Builder display(@NotNull DisplayProperties display) {
            this.display = display;
            return this;
        }

        public @NotNull Builder model(@NotNull ModelDefinition model) {
            this.model = model;
            return this;
        }

        public @NotNull Builder behaviour(@NotNull ItemBehaviour behaviour) {
            this.behaviour = behaviour;
            return this;
        }

        public @NotNull Builder java(@NotNull JavaOptions java) {
            this.java = java;
            return this;
        }

        public @NotNull Builder bedrock(@NotNull BedrockOptions bedrock) {
            this.bedrock = bedrock;
            return this;
        }

        public @NotNull ItemDefinition build() {
            // An item with no model declared still needs one; fall back to the base
            // material's own vanilla appearance rather than rendering as missing texture.
            ModelDefinition resolved = model != null
                    ? model
                    : new ModelDefinition.Vanilla(
                            Key.key("minecraft", java.baseMaterial().name().toLowerCase(Locale.ROOT)));
            return new ItemDefinition(key, display, resolved, behaviour, java, bedrock);
        }
    }
}
