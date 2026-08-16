package io.kalo.content.sound.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * A custom sound event.
 *
 * <p>Platform-neutral like the rest of the IR: it names the ogg files the pack ships and
 * how they should be picked, and nothing about how either platform registers them.</p>
 *
 * @param key      the sound event, e.g. {@code mypack:ambient.cave_wind}
 * @param sounds   the ogg files; more than one means the game picks at random
 * @param subtitle shown with subtitles enabled; {@code null} for a sound with none
 * @param category which volume slider controls it
 */
public record SoundDefinition(
        @NotNull Key key,
        @NotNull @Unmodifiable List<SoundFile> sounds,
        String subtitle,
        @NotNull SoundCategory category
) {
    public SoundDefinition {
        if (sounds.isEmpty()) {
            throw new IllegalArgumentException("a sound event needs at least one sound file");
        }
        sounds = List.copyOf(sounds);
    }

    /**
     * One ogg file within an event.
     *
     * @param file   the sound path, e.g. {@code mypack:ambient/cave_wind}
     * @param volume 0..1
     * @param pitch  playback rate; 1 is the file's own pitch
     * @param weight relative chance of being chosen when the event has several files
     */
    public record SoundFile(@NotNull Key file, float volume, float pitch, int weight) {
        public SoundFile {
            if (volume < 0 || volume > 1) {
                throw new IllegalArgumentException("volume must be within 0..1, got " + volume);
            }
            if (pitch <= 0) {
                throw new IllegalArgumentException("pitch must be positive, got " + pitch);
            }
            if (weight < 1) {
                throw new IllegalArgumentException("weight must be at least 1, got " + weight);
            }
        }

        public static @NotNull SoundFile of(@NotNull Key file) {
            return new SoundFile(file, 1.0f, 1.0f, 1);
        }
    }
}
