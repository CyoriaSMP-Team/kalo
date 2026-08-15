package io.kalo.migration;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared recipe conversion for both importers.
 *
 * <p>Oraxen and ItemsAdder differ in how they name things but agree on the shape of a
 * crafting recipe, so the pattern and ingredient handling is written once here and each
 * importer supplies its own way of reading an ingredient.</p>
 *
 * <p>The one thing that always has to be translated is the empty slot. Both plugins write
 * it as {@code _}, Kalo uses a space, and leaving it alone would make {@code _} look like
 * an ingredient character with nothing bound to it.</p>
 */
public final class RecipeImport {

    /** Both plugins mark an empty grid slot with an underscore. */
    private static final char FOREIGN_EMPTY_SLOT = '_';

    private RecipeImport() {
    }

    /** How a plugin's ingredient entry becomes a Kalo ingredient string, or null if unreadable. */
    public interface IngredientReader {
        @Nullable String read(@NotNull ConfigurationSection ingredient, @NotNull String slot);
    }

    /**
     * Converts one recipe.
     *
     * @param pattern the grid rows as the source plugin wrote them
     * @return the Kalo recipe body, ready to be written under the recipe's key
     */
    public static @NotNull Map<String, Object> convert(@NotNull String key,
                                                       @NotNull List<String> pattern,
                                                       @NotNull ConfigurationSection ingredients,
                                                       @NotNull String result,
                                                       int amount,
                                                       @NotNull IngredientReader reader,
                                                       @NotNull ImportReport report) {
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("type", "recipe");
        converted.put("result", amount > 1 ? amount + "x " + result : result);

        Map<String, Object> keys = new LinkedHashMap<>();
        for (String slot : ingredients.getKeys(false)) {
            ConfigurationSection entry = ingredients.getConfigurationSection(slot);

            String ingredient = entry != null
                    ? reader.read(entry, slot)
                    // ItemsAdder writes ingredients as plain strings rather than sections.
                    : ingredients.getString(slot);

            if (ingredient == null) {
                report.unsupported(key + ".ingredients." + slot + " (could not be read)");
                continue;
            }
            keys.put(slot, ingredient);
        }

        if (keys.isEmpty()) {
            throw new IllegalArgumentException("no readable ingredients");
        }
        converted.put("ingredients", keys);

        if (!pattern.isEmpty()) {
            converted.put("pattern", normalisePattern(pattern));
        }

        return converted;
    }

    /**
     * Rewrites empty slots and pads rows to a rectangle.
     *
     * <p>Both plugins tolerate ragged rows; Bukkit does not, and its own complaint does
     * not say which row is short.</p>
     */
    static @NotNull List<String> normalisePattern(@NotNull List<String> pattern) {
        int width = pattern.stream().mapToInt(String::length).max().orElse(0);

        return pattern.stream()
                .map(row -> row.replace(FOREIGN_EMPTY_SLOT, ' '))
                .map(row -> row.length() < width ? row + " ".repeat(width - row.length()) : row)
                .toList();
    }

    /** {@code minecraft:STICK} and {@code STICK} both mean the vanilla item. */
    public static @NotNull String vanilla(@NotNull String material) {
        String value = material.toLowerCase(Locale.ROOT);
        return value.startsWith("minecraft:") ? value : "minecraft:" + value;
    }
}
