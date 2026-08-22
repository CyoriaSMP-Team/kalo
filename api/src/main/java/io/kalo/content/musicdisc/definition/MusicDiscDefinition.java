package io.kalo.content.musicdisc.definition;

import io.kalo.content.item.definition.BedrockOptions;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-neutral description of a custom music disc.
 *
 * @param key          content key (namespace:name)
 * @param sound        sound event to play
 * @param description  display description (shown in jukebox tooltip)
 * @param duration     disc duration in seconds
 * @param comparatorOutput  redstone comparator output (1-15)
 * @param bedrock      Bedrock platform options
 */
public record MusicDiscDefinition(
        @NotNull Key key,
        @NotNull Key sound,
        @NotNull String description,
        int duration,
        int comparatorOutput,
        @NotNull BedrockOptions bedrock
) {
    public MusicDiscDefinition {
        if (duration < 1) {
            throw new IllegalArgumentException("duration must be >= 1, got " + duration);
        }
        if (comparatorOutput < 1 || comparatorOutput > 15) {
            throw new IllegalArgumentException("comparatorOutput must be 1-15, got " + comparatorOutput);
        }
    }

    public static @NotNull Builder builder(@NotNull Key key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final Key key;
        private Key sound;
        private String description;
        private int duration = 60;
        private int comparatorOutput = 7;
        private BedrockOptions bedrock = BedrockOptions.defaults();

        private Builder(@NotNull Key key) {
            this.key = key;
        }

        public @NotNull Builder sound(@NotNull Key sound) { this.sound = sound; return this; }
        public @NotNull Builder description(@NotNull String description) { this.description = description; return this; }
        public @NotNull Builder duration(int duration) { this.duration = duration; return this; }
        public @NotNull Builder comparatorOutput(int comparatorOutput) { this.comparatorOutput = comparatorOutput; return this; }
        public @NotNull Builder bedrock(@NotNull BedrockOptions bedrock) { this.bedrock = bedrock; return this; }

        public @NotNull MusicDiscDefinition build() {
            if (sound == null) {
                throw new IllegalStateException("music disc " + key.asString() + " has no sound");
            }
            if (description == null) {
                throw new IllegalStateException("music disc " + key.asString() + " has no description");
            }
            return new MusicDiscDefinition(key, sound, description, duration, comparatorOutput, bedrock);
        }
    }
}
