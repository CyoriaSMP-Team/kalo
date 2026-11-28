package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Cast-time options parsed from `/kalo vfx ...`.
 *
 * <p>Supported tokens: {@code loop}, {@code loop:<n>x}, {@code loop:<time>}, {@code quiet},
 * {@code volume:<n>}, {@code tag:<name>}, {@code end:finish}/{@code end:cut},
 * {@code speed:<n>}, {@code scale:<n>}, {@code follow}, {@code offset:<left>,<up>,<ahead>},
 * {@code turn:<degrees>}, {@code delay:<time>}, {@code seed:<n>}, {@code only:caster},
 * {@code check:<time>} and trailing {@code until:<condition>} / {@code while:<condition>}.</p>
 */
public record VfxCastOptions(
        boolean loop,
        long cycles,
        boolean quiet,
        double volume,
        @Nullable String tag,
        boolean finishOnStop,
        double speed,
        double scale,
        boolean follow,
        @NotNull VfxRecipe.Vec3 offset,
        double turn,
        int delayTicks,
        @Nullable Long seed,
        boolean onlyCaster,
        long loopTicks,
        @Nullable VfxCondition until,
        @Nullable VfxCondition whileCondition,
        int checkTicks
) {

    public static final VfxCastOptions ONCE = new VfxCastOptions(
            false, 0, false, 1.0, null, true,
            1.0, 1.0, false, VfxRecipe.Vec3.ZERO, 0, 0, null, false,
            0, null, null, 20);

    public VfxCastOptions {
        if (cycles < 0) {
            throw new IllegalArgumentException("VFX loop cycles must be >= 0");
        }
        if (volume < 0 || volume > 4) {
            throw new IllegalArgumentException("VFX volume must be within 0..4");
        }
        if (speed < 0.1 || speed > 10) {
            throw new IllegalArgumentException("VFX speed must be within 0.1..10");
        }
        if (scale < 0.1 || scale > 10) {
            throw new IllegalArgumentException("VFX scale must be within 0.1..10");
        }
        if (Math.abs(offset.x()) > 64 || Math.abs(offset.y()) > 64
                || Math.abs(offset.z()) > 64) {
            throw new IllegalArgumentException("VFX offset must be within -64..64");
        }
        if (Math.abs(turn) > 3600) {
            throw new IllegalArgumentException("VFX turn must be within -3600..3600");
        }
        if (delayTicks < 0 || delayTicks > 6000) {
            throw new IllegalArgumentException("VFX delay must be within 0..6000 ticks");
        }
        if (loopTicks < 0 || loopTicks > 72_000) {
            throw new IllegalArgumentException("VFX loop time must be within 0..72000 ticks");
        }
        if (checkTicks < 1 || checkTicks > 1200) {
            throw new IllegalArgumentException("VFX check interval must be within 1..1200 ticks");
        }
        if (tag != null && !tag.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("VFX tag must be 1..32 letters, digits, _ or -");
        }
    }

    public static @NotNull VfxCastOptions parse(@NotNull String raw) {
        if (raw.isBlank()) {
            return ONCE;
        }

        String head = raw;
        VfxCondition until = null;
        VfxCondition whileCondition = null;
        int conditionStart = conditionStart(raw);
        if (conditionStart >= 0) {
            head = raw.substring(0, conditionStart);
            String condition = raw.substring(conditionStart).trim();
            String lower = condition.toLowerCase(Locale.ROOT);
            if (lower.startsWith("until:")) {
                until = VfxCondition.parse(condition.substring("until:".length()).trim());
            } else {
                whileCondition = VfxCondition.parse(
                        condition.substring("while:".length()).trim());
            }
        }

        boolean loop = false;
        long cycles = 0;
        boolean quiet = false;
        double volume = 1.0;
        String tag = null;
        boolean finish = true;
        double speed = 1.0;
        double scale = 1.0;
        boolean follow = false;
        VfxRecipe.Vec3 offset = VfxRecipe.Vec3.ZERO;
        double turn = 0;
        int delay = 0;
        Long seed = null;
        boolean onlyCaster = false;
        long loopTicks = 0;
        int check = 20;
        for (String token : head.trim().isEmpty() ? new String[0]
                : head.trim().split("\\s+")) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (lower.equals("loop")) {
                loop = true;
            } else if (lower.startsWith("loop:")) {
                String value = lower.substring("loop:".length());
                if (value.endsWith("x")) {
                    cycles = parseLong(value.substring(0, value.length() - 1),
                            "VFX loop count", 1);
                    loopTicks = 0;
                } else {
                    loopTicks = parseDelay(value);
                    cycles = 0;
                }
                loop = true;
            } else if (lower.equals("quiet")) {
                quiet = true;
            } else if (lower.startsWith("volume:")) {
                volume = parseDouble(lower.substring("volume:".length()), "VFX volume");
            } else if (lower.startsWith("tag:")) {
                tag = token.substring("tag:".length());
            } else if (lower.equals("end:finish") || lower.equals("end:fade")) {
                finish = true;
            } else if (lower.equals("end:cut")) {
                finish = false;
            } else if (lower.startsWith("speed:")) {
                speed = parseDouble(lower.substring("speed:".length()), "VFX speed");
            } else if (lower.startsWith("scale:")) {
                scale = parseDouble(lower.substring("scale:".length()), "VFX scale");
            } else if (lower.equals("follow")) {
                follow = true;
            } else if (lower.startsWith("offset:")) {
                offset = parseOffset(lower.substring("offset:".length()));
            } else if (lower.startsWith("turn:")) {
                turn = parseDouble(lower.substring("turn:".length()), "VFX turn");
            } else if (lower.startsWith("delay:")) {
                delay = parseDelay(lower.substring("delay:".length()));
            } else if (lower.startsWith("seed:")) {
                seed = parseLong(lower.substring("seed:".length()), "VFX seed", Long.MIN_VALUE);
            } else if (lower.equals("only:caster")) {
                onlyCaster = true;
            } else if (lower.equals("only:all")) {
                onlyCaster = false;
            } else if (lower.startsWith("check:")) {
                check = parseDelay(lower.substring("check:".length()));
            } else {
                throw new IllegalArgumentException("Unknown VFX option '" + token + "'");
            }
        }
        return new VfxCastOptions(loop, cycles, quiet, volume, tag, finish,
                speed, scale, follow, offset, turn, delay, seed, onlyCaster,
                loopTicks, until, whileCondition, check);
    }

    /** A tag supplied by the caller, or the effect key for untagged casts. */
    public @NotNull String tagOr(@NotNull String fallback) {
        return tag == null ? fallback : tag;
    }

    private static int conditionStart(@NotNull String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        for (int index = 0; index < lower.length(); index++) {
            if (index > 0 && !Character.isWhitespace(raw.charAt(index - 1))) {
                continue;
            }
            if (lower.startsWith("until:", index) || lower.startsWith("while:", index)) {
                return index;
            }
        }
        return -1;
    }

    private static @NotNull VfxRecipe.Vec3 parseOffset(@NotNull String value) {
        String[] parts = value.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "VFX offset must be left,up,ahead (e.g. offset:0,0,3)");
        }
        try {
            return new VfxRecipe.Vec3(Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid VFX offset '" + value + "'");
        }
    }

    private static int parseDelay(@NotNull String value) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (text.endsWith("s")) {
                return (int) Math.round(Double.parseDouble(
                        text.substring(0, text.length() - 1)) * 20);
            }
            if (text.endsWith("m")) {
                return (int) Math.round(Double.parseDouble(
                        text.substring(0, text.length() - 1)) * 1200);
            }
            if (text.endsWith("t")) {
                text = text.substring(0, text.length() - 1);
            }
            return (int) Math.round(Double.parseDouble(text));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid VFX time '" + value + "'");
        }
    }

    private static long parseLong(@NotNull String value, @NotNull String label,
                                  long minimum) {
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < minimum) {
                throw new IllegalArgumentException(label + " is too small");
            }
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid " + label + " '" + value + "'");
        }
    }

    private static double parseDouble(@NotNull String value, @NotNull String label) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid " + label + " '" + value + "'");
        }
    }
}
