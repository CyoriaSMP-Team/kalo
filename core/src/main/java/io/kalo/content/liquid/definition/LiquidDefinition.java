package io.kalo.content.liquid.definition;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * A marked liquid: real water or lava plus the rules Kalo applies inside a saved volume.
 *
 * <p>Minecraft cannot add a third fluid, so the base block stays vanilla water/lava. The
 * volume is what distinguishes acid from an ocean, and the optional biome id supplies the
 * water tint.</p>
 */
public record LiquidDefinition(
        @NotNull Key key,
        @NotNull String name,
        @NotNull Base base,
        @Nullable Integer color,
        @Nullable String effect,
        int amplifier,
        double damage,
        boolean fireproof
) {

    public enum Base {
        WATER,
        LAVA;

        public static @NotNull Base parse(@NotNull String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "water", "waterlogged" -> WATER;
                case "lava" -> LAVA;
                default -> throw new IllegalArgumentException(
                        "liquid base must be water or lava, got '" + value + "'");
            };
        }

        public @NotNull Material material() {
            return this == WATER ? Material.WATER : Material.LAVA;
        }
    }

    public LiquidDefinition {
        if (name.isBlank() || name.length() > 120
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("liquid name is invalid");
        }
        name = name.strip();
        if (color != null && (color < 0 || color > 0xFFFFFF)) {
            throw new IllegalArgumentException("liquid color must be within 0..0xFFFFFF");
        }
        effect = effect == null ? null : effect.strip();
        if (effect != null && (effect.isBlank() || effect.length() > 64
                || !effect.matches("[A-Za-z0-9_:/.-]+"))) {
            throw new IllegalArgumentException("liquid effect is invalid");
        }
        if (amplifier < 0 || amplifier > 255) {
            throw new IllegalArgumentException("liquid amplifier must be within 0..255");
        }
        if (damage < 0 || damage > 100) {
            throw new IllegalArgumentException("liquid damage must be within 0..100");
        }
    }

    /** The generated biome id, e.g. {@code mypack:liquid/acid}. */
    public @NotNull Key biomeKey() {
        return Key.key(key.namespace(), "liquid/" + key.value());
    }

    public static int parseColor(@NotNull String value) {
        String color = value.trim();
        Map<String, Integer> dyes = Map.ofEntries(
                Map.entry("white", 0xFFFFFF), Map.entry("orange", 0xFFA500),
                Map.entry("magenta", 0xFF00FF), Map.entry("light_blue", 0x55FFFF),
                Map.entry("yellow", 0xFFFF55), Map.entry("lime", 0x55FF55),
                Map.entry("pink", 0xFFB6C1), Map.entry("gray", 0x808080),
                Map.entry("light_gray", 0xD3D3D3), Map.entry("cyan", 0x00AAAA),
                Map.entry("purple", 0xAA00AA), Map.entry("blue", 0x5555FF),
                Map.entry("brown", 0xAA5500), Map.entry("green", 0x00AA00),
                Map.entry("red", 0xFF5555), Map.entry("black", 0x000000));
        Integer dye = dyes.get(color.toLowerCase(Locale.ROOT));
        if (dye != null) {
            return dye;
        }
        if (color.startsWith("#")) {
            color = color.substring(1);
        } else if (color.startsWith("0x") || color.startsWith("0X")) {
            color = color.substring(2);
        }
        if (!color.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("color must be a dye name or six hex digits");
        }
        return Integer.parseInt(color, 16);
    }
}
