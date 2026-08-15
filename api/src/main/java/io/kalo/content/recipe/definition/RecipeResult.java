package io.kalo.content.recipe.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * What a recipe produces.
 *
 * @param content the Kalo content key to produce
 * @param amount  how many, 1..99
 */
public record RecipeResult(@NotNull Key content, int amount) {

    public RecipeResult {
        if (amount < 1 || amount > 99) {
            throw new IllegalArgumentException("result amount must be within 1..99, got " + amount);
        }
    }

    public static @NotNull RecipeResult of(@NotNull Key content) {
        return new RecipeResult(content, 1);
    }
}
