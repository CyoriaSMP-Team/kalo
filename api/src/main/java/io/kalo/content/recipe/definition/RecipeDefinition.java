package io.kalo.content.recipe.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * A crafting recipe, described without naming a platform.
 *
 * <p>Ingredients are {@link RecipeIngredient}s rather than Bukkit item stacks so the same
 * recipe can be registered on Java and translated for Bedrock, where recipes are declared
 * in the pack rather than through an API.</p>
 */
public sealed interface RecipeDefinition {

    @NotNull Key key();

    /** What the recipe produces. */
    @NotNull RecipeResult result();

    /** Cooking stations that smelt or smoke an input into a result. */
    enum CookingStation {
        FURNACE,
        BLAST_FURNACE,
        SMOKER,
        CAMPFIRE;

        public static @NotNull CookingStation fromString(@NotNull String value) {
            return switch (value.toLowerCase(java.util.Locale.ROOT)) {
                case "furnace" -> FURNACE;
                case "blast_furnace", "blasting" -> BLAST_FURNACE;
                case "smoker", "smoking" -> SMOKER;
                case "campfire", "campfire_cooking" -> CAMPFIRE;
                default -> throw new IllegalArgumentException("unknown cooking station '" + value
                        + "'; expected furnace, blast_furnace, smoker or campfire");
            };
        }
    }

    /**
     * A recipe laid out on the grid.
     *
     * @param pattern up to three rows, each up to three characters; a space is an empty slot
     * @param keys    maps each pattern character to an ingredient
     */
    record Shaped(
            @NotNull Key key,
            @NotNull RecipeResult result,
            @NotNull @Unmodifiable List<String> pattern,
            @NotNull @Unmodifiable Map<Character, RecipeIngredient> keys
    ) implements RecipeDefinition {

        public Shaped {
            pattern = List.copyOf(pattern);
            keys = Map.copyOf(keys);
            if (pattern.isEmpty() || pattern.size() > 3) {
                throw new IllegalArgumentException("a shaped pattern needs 1..3 rows, got " + pattern.size());
            }
            int width = pattern.getFirst().length();
            if (width < 1) {
                throw new IllegalArgumentException("a shaped pattern row cannot be empty");
            }
            Set<Character> used = new HashSet<>();
            for (String row : pattern) {
                if (row.length() != width) {
                    throw new IllegalArgumentException(
                            "every pattern row must be the same length; '" + row + "' is "
                                    + row.length() + " but the first row is " + width);
                }
                if (row.length() > 3) {
                    throw new IllegalArgumentException("pattern row '" + row + "' is longer than 3");
                }
                for (char slot : row.toCharArray()) {
                    if (slot != ' ' && !keys.containsKey(slot)) {
                        // Caught here rather than at registration: the server would
                        // otherwise reject the recipe with a message that does not say
                        // which character was missing.
                        throw new IllegalArgumentException(
                                "pattern uses '" + slot + "' but no ingredient is defined for it");
                    }
                    if (slot != ' ') {
                        used.add(slot);
                    }
                }
            }
            if (used.isEmpty()) {
                throw new IllegalArgumentException("a shaped pattern needs at least one ingredient");
            }
            for (Character slot : keys.keySet()) {
                if (slot == ' ') {
                    throw new IllegalArgumentException("space is reserved for an empty recipe slot");
                }
                if (!used.contains(slot)) {
                    throw new IllegalArgumentException(
                            "ingredient '" + slot + "' is defined but never used in the pattern");
                }
            }
        }
    }

    /** A recipe where only the set of ingredients matters. */
    record Shapeless(
            @NotNull Key key,
            @NotNull RecipeResult result,
            @NotNull @Unmodifiable List<RecipeIngredient> ingredients
    ) implements RecipeDefinition {

        public Shapeless {
            ingredients = List.copyOf(ingredients);
            if (ingredients.isEmpty() || ingredients.size() > 9) {
                throw new IllegalArgumentException(
                        "a shapeless recipe needs 1..9 ingredients, got " + ingredients.size());
            }
        }
    }

    /** A cooking recipe (furnace, blast furnace, smoker, campfire). */
    record Cooking(
            @NotNull Key key,
            @NotNull RecipeResult result,
            @NotNull RecipeIngredient input,
            @NotNull CookingStation station,
            float experience,
            int cookingTime
    ) implements RecipeDefinition {
        public Cooking {
            if (cookingTime < 1) {
                throw new IllegalArgumentException("cookingTime must be >= 1, got " + cookingTime);
            }
            if (experience < 0) {
                throw new IllegalArgumentException("experience must be >= 0, got " + experience);
            }
        }
    }

    /** A stonecutter recipe. */
    record Stonecutting(
            @NotNull Key key,
            @NotNull RecipeResult result,
            @NotNull RecipeIngredient input
    ) implements RecipeDefinition {
    }

    /** A smithing transform recipe (base + addition -> result). */
    record Smithing(
            @NotNull Key key,
            @NotNull RecipeResult result,
            @NotNull RecipeIngredient base,
            @NotNull RecipeIngredient addition
    ) implements RecipeDefinition {
    }
}
