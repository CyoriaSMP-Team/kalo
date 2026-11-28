package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The ten ready-made Studio presets, expressed as Kalo recipes.
 *
 * <p>These are independent Kalo recipes with the same public names as Studio's presets;
 * no Studio or RP Engine recipe data is copied. They are deliberately simple, deterministic
 * shapes and colours, and can be replaced by an authored recipe or Studio JSON.</p>
 */
public final class VfxPresets {

    public static final Set<String> NAMES = Set.of(
            "void_slash", "frost_nova", "thunderstorm", "pillar_of_light", "black_hole",
            "starfall", "sanctuary_bloom", "rift_eye", "tornado", "aegis_dome");

    private VfxPresets() {
    }

    public static @NotNull Optional<VfxRecipe> recipe(@NotNull String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (!NAMES.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(switch (key) {
            case "void_slash" -> recipe(24, List.of(
                    layer("streak", 0, 24, 60, VfxRecipe.Shape.LINE, 3.2, 0.35, 0.8,
                            0.38, 14, 0, 0x7B3CFF),
                    layer("glow", 2, 24, 40, VfxRecipe.Shape.RING, 2.0, 0.2, 0.5,
                            0.28, 18, 0, 0xC8A2FF)));
            case "frost_nova" -> recipe(40, List.of(
                    layer("glow", 0, 40, 90, VfxRecipe.Shape.DISC, 3.4, 0.25, 0.6,
                            0.3, 24, 0, 0x9FE8FF),
                    layer("spark", 8, 40, 60, VfxRecipe.Shape.RING, 2.7, 0.35, 0.75,
                            0.22, 18, 0, 0xE8FFFF)));
            case "thunderstorm" -> recipe(80, List.of(
                    layer("streak", 0, 80, 120, VfxRecipe.Shape.SPHERE, 4.5, 0.2, 0.9,
                            0.3, 32, 0.02, 0xFFF27A),
                    layer("spark", 10, 80, 60, VfxRecipe.Shape.CONE, 3.0, 0.4, 1.1,
                            0.24, 26, 0.05, 0xFFFFFF)));
            case "pillar_of_light" -> recipe(60, List.of(
                    layer("streak", 0, 60, 90, VfxRecipe.Shape.LINE, 5.0, 0.1, 0.35,
                            0.34, 30, 0.08, 0xFFF7C0),
                    layer("glow", 0, 60, 60, VfxRecipe.Shape.RING, 2.2, 0.15, 0.4,
                            0.25, 24, 0, 0xFFFFFF)));
            case "black_hole" -> recipe(100, List.of(
                    layer("glow", 0, 100, 140, VfxRecipe.Shape.SPHERE, 4.8, 0.05, 0.45,
                            0.3, 40, 0, 0x4B1A7A),
                    layer("spark", 6, 100, 60, VfxRecipe.Shape.RING, 3.8, 0.5, 1.2,
                            0.22, 34, -0.02, 0xD9A6FF)));
            case "starfall" -> recipe(70, List.of(
                    layer("streak", 0, 70, 120, VfxRecipe.Shape.CONE, 4.0, 0.5, 1.4,
                            0.3, 26, -0.15, 0xFFE08A),
                    layer("spark", 15, 70, 50, VfxRecipe.Shape.SPHERE, 5.0, 0.2, 0.6,
                            0.22, 22, -0.05, 0xFFFFFF)));
            case "sanctuary_bloom" -> recipe(60, List.of(
                    layer("glow", 0, 60, 120, VfxRecipe.Shape.SPHERE, 3.6, 0.15, 0.55,
                            0.28, 28, 0.01, 0x7CFFB2),
                    layer("spark", 10, 60, 50, VfxRecipe.Shape.DISC, 4.2, 0.3, 0.7,
                            0.2, 20, 0, 0xE8FFD8)));
            case "rift_eye" -> recipe(50, List.of(
                    layer("glow", 0, 50, 90, VfxRecipe.Shape.RING, 2.5, 0.2, 0.6,
                            0.3, 26, 0, 0xB04CFF),
                    layer("spark", 5, 50, 60, VfxRecipe.Shape.SPHERE, 1.8, 0.4, 1.0,
                            0.2, 20, 0, 0xF0C8FF)));
            case "tornado" -> recipe(80, List.of(
                    layer("streak", 0, 80, 140, VfxRecipe.Shape.SPHERE, 3.2, 0.35, 0.9,
                            0.3, 30, 0.03, 0x9AA4B2),
                    layer("glow", 8, 80, 60, VfxRecipe.Shape.RING, 2.2, 0.2, 0.5,
                            0.24, 24, 0.02, 0xDDE6F2)));
            case "aegis_dome" -> recipe(70, List.of(
                    layer("glow", 0, 70, 160, VfxRecipe.Shape.SPHERE, 4.6, 0.05, 0.3,
                            0.3, 36, 0, 0x66D9FF),
                    layer("spark", 10, 70, 60, VfxRecipe.Shape.RING, 4.6, 0.15, 0.45,
                            0.24, 28, 0, 0xE0FAFF)));
            default -> throw new IllegalStateException("unreachable preset " + key);
        });
    }

    public static @NotNull String displayName(@NotNull String name) {
        String[] words = name.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return result.toString();
    }

    private static @NotNull VfxRecipe recipe(int duration, @NotNull List<VfxRecipe.Layer> layers) {
        return new VfxRecipe(duration, null, layers, List.of());
    }

    private static @NotNull VfxRecipe.Layer layer(
            @NotNull String kind, int start, int end, int burst,
            @NotNull VfxRecipe.Shape shape, double radius, double speedMin, double speedMax,
            double size, int lifetime, double gravity, int color) {
        return new VfxRecipe.Layer(kind, start, end, burst, VfxRecipe.Vec3.ZERO, shape,
                radius, speedMin, speedMax, size, lifetime, gravity, color);
    }
}
