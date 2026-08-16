package io.kalo.content.sound.definition;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Which volume slider controls a sound.
 *
 * <p>Its own enum rather than Bukkit's {@code SoundCategory} for the usual reason: the
 * definition layer names no platform type. Both platforms have the same set, so this maps
 * cleanly either way.</p>
 */
public enum SoundCategory {
    MASTER,
    MUSIC,
    RECORDS,
    WEATHER,
    BLOCKS,
    HOSTILE,
    NEUTRAL,
    PLAYERS,
    AMBIENT,
    VOICE;

    /** The name vanilla uses in {@code sounds.json}. */
    public @NotNull String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @NotNull SoundCategory fromId(@NotNull String id) {
        for (SoundCategory category : values()) {
            if (category.id().equalsIgnoreCase(id)) {
                return category;
            }
        }
        throw new IllegalArgumentException("unknown sound category '" + id + "'");
    }
}
