package io.kalo.content.recipe;

import io.kalo.content.recipe.definition.RecipeDefinition;
import io.kalo.content.recipe.definition.RecipeIngredient;
import io.kalo.content.recipe.definition.RecipeResult;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Parses recipe YAML into {@link RecipeDefinition}s.
 *
 * <pre>
 * ruby_sword_recipe:
 *   type: recipe
 *   result: ruby_sword          # or "mypack:ruby_sword", or "3x ruby_sword"
 *   pattern:
 *     - " R "
 *     - " R "
 *     - " S "
 *   ingredients:
 *     R: mypack:ruby            # another Kalo item
 *     S: minecraft:stick        # a vanilla item
 * </pre>
 *
 * <p>A recipe with {@code ingredients} but no {@code pattern} is shapeless.</p>
 */
public final class RecipeParser {

    private RecipeParser() {
    }

    public static @NotNull RecipeDefinition parse(@NotNull Key key, @NotNull ConfigurationSection config) {
        RecipeResult result = parseResult(key.namespace(),
                Objects.requireNonNull(config.getString("result"), "recipe is missing a result"));

        String stationRaw = config.getString("station");
        if (stationRaw != null) {
            String station = stationRaw.trim().toLowerCase(Locale.ROOT);
            switch (station) {
                case "furnace", "blast_furnace", "blasting", "smoker", "smoking", "campfire",
                     "campfire_cooking" -> {
                    RecipeDefinition.CookingStation cookingStation =
                            RecipeDefinition.CookingStation.fromString(station);
                    String inputRaw = config.getString("input");
                    if (inputRaw == null) {
                        // Accept ingredients.<single> as alternative for cooking
                        ConfigurationSection ing = config.getConfigurationSection("ingredients");
                        if (ing != null && ing.getKeys(false).size() == 1) {
                            inputRaw = ing.getString(ing.getKeys(false).iterator().next());
                        }
                    }
                    if (inputRaw == null) {
                        throw new IllegalArgumentException(
                                "cooking recipe '" + key.asString() + "' needs 'input' (station: " + stationRaw + ")");
                    }
                    float experience = (float) config.getDouble("experience", 0.0);
                    int cookingTime = config.getInt("cooking_time", config.getInt("cookingTime", 200));
                    return new RecipeDefinition.Cooking(
                            key, result, parseIngredient(key.namespace(), inputRaw),
                            cookingStation, experience, cookingTime);
                }
                case "stonecutting", "stonecutter" -> {
                    String inputRaw = config.getString("input");
                    if (inputRaw == null) {
                        ConfigurationSection ing = config.getConfigurationSection("ingredients");
                        if (ing != null && ing.getKeys(false).size() == 1) {
                            inputRaw = ing.getString(ing.getKeys(false).iterator().next());
                        }
                    }
                    if (inputRaw == null) {
                        throw new IllegalArgumentException(
                                "stonecutting recipe '" + key.asString() + "' needs 'input'");
                    }
                    return new RecipeDefinition.Stonecutting(
                            key, result, parseIngredient(key.namespace(), inputRaw));
                }
                case "smithing", "smithing_transform" -> {
                    String baseRaw = config.getString("base");
                    String additionRaw = config.getString("addition");
                    if (baseRaw == null || additionRaw == null) {
                        throw new IllegalArgumentException(
                                "smithing recipe '" + key.asString() + "' needs 'base' and 'addition'");
                    }
                    return new RecipeDefinition.Smithing(
                            key, result,
                            parseIngredient(key.namespace(), baseRaw),
                            parseIngredient(key.namespace(), additionRaw));
                }
                default -> throw new IllegalArgumentException(
                        "unknown station '" + stationRaw + "' for recipe '" + key.asString()
                                + "'; expected furnace, blast_furnace, smoker, campfire, stonecutting or smithing");
            }
        }

        List<String> pattern = config.getStringList("pattern");
        ConfigurationSection ingredients = config.getConfigurationSection("ingredients");
        if (ingredients == null) {
            throw new IllegalArgumentException("recipe is missing an ingredients section");
        }

        if (pattern.isEmpty()) {
            List<RecipeIngredient> shapeless = new ArrayList<>();
            for (String slot : ingredients.getKeys(false)) {
                String value = ingredients.getString(slot);
                if (value != null) {
                    shapeless.add(parseIngredient(key.namespace(), value));
                }
            }
            return new RecipeDefinition.Shapeless(key, result, shapeless);
        }

        Map<Character, RecipeIngredient> keys = new LinkedHashMap<>();
        for (String slot : ingredients.getKeys(false)) {
            if (slot.length() != 1) {
                throw new IllegalArgumentException(
                        "ingredient key '" + slot + "' must be a single character to match the pattern");
            }
            String value = ingredients.getString(slot);
            if (value != null) {
                keys.put(slot.charAt(0), parseIngredient(key.namespace(), value));
            }
        }

        return new RecipeDefinition.Shaped(key, result, pattern, keys);
    }

    /** Accepts {@code ruby_sword} and {@code 4x ruby_sword}. */
    static @NotNull RecipeResult parseResult(@NotNull String fallbackNamespace, @NotNull String value) {
        String trimmed = value.trim();
        int amount = 1;

        int marker = trimmed.indexOf('x');
        if (marker > 0 && trimmed.substring(0, marker).chars().allMatch(Character::isDigit)) {
            amount = Integer.parseInt(trimmed.substring(0, marker));
            trimmed = trimmed.substring(marker + 1).trim();
        }

        return new RecipeResult(resolveKey(fallbackNamespace, trimmed), amount);
    }

    /**
     * {@code minecraft:*} is a vanilla material; anything else is Kalo content.
     *
     * <p>The namespace is what decides, not a guess at whether the name looks like a
     * material — a pack is free to define {@code mypack:diamond}, and it must not
     * silently resolve to the vanilla one.</p>
     */
    static @NotNull RecipeIngredient parseIngredient(@NotNull String fallbackNamespace, @NotNull String value) {
        Key key = resolveKey(fallbackNamespace, value.trim());
        if (key.namespace().equals("minecraft")) {
            return new RecipeIngredient.Vanilla(key);
        }
        return new RecipeIngredient.Content(key);
    }

    private static @NotNull Key resolveKey(@NotNull String fallbackNamespace, @NotNull String value) {
        int separator = value.indexOf(':');
        if (separator < 0) {
            // Unqualified names mean "in this pack", which is what a pack author almost
            // always intends when referring to their own content.
            return Key.key(fallbackNamespace, value.toLowerCase(Locale.ROOT));
        }
        return Key.key(value.substring(0, separator), value.substring(separator + 1).toLowerCase(Locale.ROOT));
    }
}
