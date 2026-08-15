package io.kalo.pack;

import org.jetbrains.annotations.NotNull;

/**
 * The contents of {@code pack.mcmeta}.
 *
 * @param format           the pack format the pack is authored against
 * @param minFormat        lowest pack format advertised as supported
 * @param maxFormat        highest pack format advertised as supported
 * @param description      shown in the client's resource pack list
 */
public record PackMeta(
        int format,
        int minFormat,
        int maxFormat,
        @NotNull String description
) {
    public PackMeta {
        if (minFormat > maxFormat) {
            throw new IllegalArgumentException(
                    "minFormat (" + minFormat + ") is greater than maxFormat (" + maxFormat + ")");
        }
    }

    public static @NotNull PackMeta of(int format, @NotNull String description) {
        return new PackMeta(format, format, format, description);
    }
}
