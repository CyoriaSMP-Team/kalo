package io.kalo.content.emote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Parses and validates the cast names passed to {@code /kalo emote}. */
public final class EmoteCast {

    private EmoteCast() {
    }

    public static @NotNull List<String> tokens(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : raw.trim().split("[,\\s]+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return List.copyOf(tokens);
    }

    /**
     * @return null when the cast is valid, otherwise a message naming the problem
     */
    public static @Nullable String validate(int required, @NotNull List<String> names,
                                            @NotNull String leadName) {
        if (names.size() != required) {
            return "This emote needs " + required + " extra performer(s), got "
                    + names.size();
        }
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.equals(leadName.toLowerCase(Locale.ROOT))) {
                return "The lead cannot also be a cast member";
            }
            if (!seen.add(normalized)) {
                return "Cast member '" + name + "' is listed twice";
            }
        }
        return null;
    }
}
