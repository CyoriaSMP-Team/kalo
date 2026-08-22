package io.kalo.content.painting.definition;

import io.kalo.content.item.definition.BedrockOptions;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-neutral description of a custom painting.
 *
 * <p>Paintings are defined by their dimensions (width × height in blocks),
 * the asset ID for the texture, and optional author/title metadata.</p>
 *
 * @param key          content key (namespace:name)
 * @param width        painting width in blocks (1-4)
 * @param height       painting height in blocks (1-4)
 * @param assetId      texture asset identifier
 * @param author       painting author name
 * @param title        painting display title
 * @param animated     whether the painting has frame animation
 * @param frameDuration ticks per frame (if animated)
 * @param bedrock      Bedrock platform options
 */
public record PaintingDefinition(
        @NotNull Key key,
        int width,
        int height,
        @NotNull String assetId,
        @Nullable String author,
        @Nullable String title,
        boolean animated,
        int frameDuration,
        @NotNull BedrockOptions bedrock
) {
    public PaintingDefinition {
        if (width < 1 || width > 4) {
            throw new IllegalArgumentException("width must be 1-4, got " + width);
        }
        if (height < 1 || height > 4) {
            throw new IllegalArgumentException("height must be 1-4, got " + height);
        }
        if (frameDuration < 1) {
            throw new IllegalArgumentException("frameDuration must be >= 1, got " + frameDuration);
        }
    }

    public static @NotNull Builder builder(@NotNull Key key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final Key key;
        private int width = 1;
        private int height = 1;
        private String assetId;
        private String author;
        private String title;
        private boolean animated = false;
        private int frameDuration = 20;
        private BedrockOptions bedrock = BedrockOptions.defaults();

        private Builder(@NotNull Key key) {
            this.key = key;
        }

        public @NotNull Builder width(int width) { this.width = width; return this; }
        public @NotNull Builder height(int height) { this.height = height; return this; }
        public @NotNull Builder assetId(@NotNull String assetId) { this.assetId = assetId; return this; }
        public @NotNull Builder author(@Nullable String author) { this.author = author; return this; }
        public @NotNull Builder title(@Nullable String title) { this.title = title; return this; }
        public @NotNull Builder animated(boolean animated) { this.animated = animated; return this; }
        public @NotNull Builder frameDuration(int frameDuration) { this.frameDuration = frameDuration; return this; }
        public @NotNull Builder bedrock(@NotNull BedrockOptions bedrock) { this.bedrock = bedrock; return this; }

        public @NotNull PaintingDefinition build() {
            if (assetId == null) {
                throw new IllegalStateException("painting " + key.asString() + " has no asset_id");
            }
            return new PaintingDefinition(key, width, height, assetId, author, title, animated, frameDuration, bedrock);
        }
    }
}
