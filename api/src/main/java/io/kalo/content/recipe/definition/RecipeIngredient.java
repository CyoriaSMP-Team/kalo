package io.kalo.content.recipe.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * One slot's worth of input.
 *
 * <p>Split into two cases because the distinction is load-bearing: a vanilla ingredient is
 * a material every platform already knows, while a Kalo ingredient names content this
 * plugin defines and can only be matched by reading the item's own marker.</p>
 */
public sealed interface RecipeIngredient {

    /** A vanilla item, named the way the game names it, e.g. {@code minecraft:diamond}. */
    record Vanilla(@NotNull Key item) implements RecipeIngredient {
    }

    /** Another piece of Kalo content, e.g. {@code mypack:ruby}. */
    record Content(@NotNull Key key) implements RecipeIngredient {
    }
}
