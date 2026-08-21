package io.kalo.platform.java;

import io.kalo.content.item.ItemImpl;
import io.kalo.content.recipe.definition.RecipeDefinition;
import io.kalo.content.recipe.definition.RecipeIngredient;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Enforces Kalo ingredient ids after Bukkit has matched their carrier materials. */
public final class JavaRecipeListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(@NotNull PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        RecipeDefinition matched = matching(JavaRecipeCompiler.activeRecipes(), matrix);
        if (matched != null) {
            // Bukkit may have picked another recipe whose MaterialChoices look identical
            // (two custom PAPER recipes, for example). Persistent ids choose the result.
            event.getInventory().setResult(JavaRecipeCompiler.resultStack(matched.result()));
        } else if (JavaRecipeCompiler.definitionOf(event.getRecipe()) != null || containsKaloContent(matrix)) {
            // A plain carrier cannot satisfy a custom ingredient, and a custom carrier
            // does not become vanilla PAPER/NOTE_BLOCK merely because no Kalo recipe won.
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(@NotNull CraftItemEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        RecipeDefinition matched = matching(JavaRecipeCompiler.activeRecipes(), matrix);
        if (matched != null) {
            ItemStack result = JavaRecipeCompiler.resultStack(matched.result());
            if (result == null) {
                event.setCancelled(true);
            } else {
                // Reassert the id-selected result at the security boundary as well as in
                // PrepareItemCraftEvent; another plugin may have changed the preview.
                event.setCurrentItem(result);
            }
        } else if (JavaRecipeCompiler.definitionOf(event.getRecipe()) != null || containsKaloContent(matrix)) {
            event.setCancelled(true);
        }
    }

    static boolean matches(@NotNull RecipeDefinition definition, ItemStack @NotNull [] matrix) {
        return matches(definition, identities(matrix));
    }

    static boolean matches(@NotNull RecipeDefinition definition,
                           StackIdentity @NotNull [] matrix) {
        int gridWidth = switch (matrix.length) {
            case 4 -> 2;
            case 9 -> 3;
            default -> {
                int width = (int) Math.sqrt(matrix.length);
                yield width * width == matrix.length ? width : -1;
            }
        };
        if (gridWidth < 1) {
            return false;
        }

        return switch (definition) {
            case RecipeDefinition.Shaped shaped -> matchesShaped(shaped, matrix, gridWidth);
            case RecipeDefinition.Shapeless shapeless -> matchesShapeless(shapeless, matrix);
            case RecipeDefinition.Cooking ignored -> false;
            case RecipeDefinition.Stonecutting ignored -> false;
            case RecipeDefinition.Smithing ignored -> false;
        };
    }

    private static boolean matchesShaped(@NotNull RecipeDefinition.Shaped recipe,
                                         StackIdentity @NotNull [] matrix,
                                         int gridWidth) {
        int patternHeight = recipe.pattern().size();
        int patternWidth = recipe.pattern().getFirst().length();
        if (patternHeight > gridWidth || patternWidth > gridWidth) {
            return false;
        }

        for (int rowOffset = 0; rowOffset <= gridWidth - patternHeight; rowOffset++) {
            for (int columnOffset = 0; columnOffset <= gridWidth - patternWidth; columnOffset++) {
                if (matchesAt(recipe, matrix, gridWidth, rowOffset, columnOffset, false)
                        || (patternWidth > 1
                        && matchesAt(recipe, matrix, gridWidth, rowOffset, columnOffset, true))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesAt(@NotNull RecipeDefinition.Shaped recipe,
                                     StackIdentity @NotNull [] matrix,
                                     int gridWidth,
                                     int rowOffset,
                                     int columnOffset,
                                     boolean mirrored) {
        int patternHeight = recipe.pattern().size();
        int patternWidth = recipe.pattern().getFirst().length();

        for (int row = 0; row < gridWidth; row++) {
            for (int column = 0; column < gridWidth; column++) {
                RecipeIngredient expected = null;
                int patternRow = row - rowOffset;
                int patternColumn = column - columnOffset;
                if (patternRow >= 0 && patternRow < patternHeight
                        && patternColumn >= 0 && patternColumn < patternWidth) {
                    if (mirrored) {
                        patternColumn = patternWidth - patternColumn - 1;
                    }
                    char symbol = recipe.pattern().get(patternRow).charAt(patternColumn);
                    if (symbol != ' ') {
                        expected = recipe.keys().get(symbol);
                    }
                }
                if (!matchesIngredient(expected, matrix[row * gridWidth + column])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesShapeless(@NotNull RecipeDefinition.Shapeless recipe,
                                            StackIdentity @NotNull [] matrix) {
        List<StackIdentity> actual = new ArrayList<>();
        for (StackIdentity stack : matrix) {
            if (!isEmpty(stack)) {
                actual.add(stack);
            }
        }
        if (actual.size() != recipe.ingredients().size()) {
            return false;
        }
        return matchUnordered(recipe.ingredients(), actual, new boolean[actual.size()], 0);
    }

    /** Backtracking avoids a vanilla carrier greedily consuming a slot needed by custom content. */
    private static boolean matchUnordered(@NotNull List<RecipeIngredient> expected,
                                          @NotNull List<StackIdentity> actual,
                                          boolean @NotNull [] used,
                                          int index) {
        if (index == expected.size()) {
            return true;
        }
        for (int slot = 0; slot < actual.size(); slot++) {
            if (!used[slot] && matchesIngredient(expected.get(index), actual.get(slot))) {
                used[slot] = true;
                if (matchUnordered(expected, actual, used, index + 1)) {
                    return true;
                }
                used[slot] = false;
            }
        }
        return false;
    }

    private static boolean matchesIngredient(@Nullable RecipeIngredient expected,
                                             @Nullable StackIdentity actual) {
        if (expected == null) {
            return isEmpty(actual);
        }
        if (isEmpty(actual)) {
            return false;
        }

        return switch (expected) {
            case RecipeIngredient.Vanilla vanilla -> {
                Material material = Material.matchMaterial(vanilla.item().value());
                yield material != null && actual.material() == material;
            }
            case RecipeIngredient.Content content ->
                    actual.contentIds().contains(content.key().asString());
        };
    }

    private static boolean isEmpty(@Nullable StackIdentity stack) {
        return stack == null || isAir(stack.material());
    }

    private static boolean isAir(@NotNull Material material) {
        // Material#isAir resolves Paper's runtime registry. Comparing the three concrete
        // air constants keeps the pure matching core usable in ordinary unit tests.
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR;
    }

    static @Nullable RecipeDefinition matching(@NotNull Iterable<RecipeDefinition> recipes,
                                               ItemStack @NotNull [] matrix) {
        return matching(recipes, identities(matrix));
    }

    static @Nullable RecipeDefinition matching(@NotNull Iterable<RecipeDefinition> recipes,
                                               StackIdentity @NotNull [] matrix) {
        List<RecipeDefinition> ordered = new ArrayList<>();
        recipes.forEach(ordered::add);
        ordered.sort(Comparator.comparing(definition -> definition.key().asString()));
        for (RecipeDefinition definition : ordered) {
            if (matches(definition, matrix)) {
                return definition;
            }
        }
        return null;
    }

    private static boolean containsKaloContent(ItemStack @NotNull [] matrix) {
        for (StackIdentity identity : identities(matrix)) {
            if (identity != null && !identity.contentIds().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static StackIdentity @NotNull [] identities(ItemStack @NotNull [] matrix) {
        StackIdentity[] identities = new StackIdentity[matrix.length];
        for (int index = 0; index < matrix.length; index++) {
            identities[index] = identityOf(matrix[index]);
        }
        return identities;
    }

    private static @Nullable StackIdentity identityOf(@Nullable ItemStack stack) {
        if (stack == null || isAir(stack.getType())) {
            return null;
        }
        Set<String> ids = new LinkedHashSet<>();
        String itemId = ItemImpl.idOf(stack);
        if (itemId != null) {
            ids.add(itemId);
        }
        String blockId = JavaBlockItemCompiler.idOf(stack);
        if (blockId != null) {
            ids.add(blockId);
        }
        return new StackIdentity(stack.getType(), ids);
    }

    /** The only stack properties recipe identity is allowed to observe; display metadata is deliberately absent. */
    record StackIdentity(@NotNull Material material, @NotNull Set<String> contentIds) {
        StackIdentity {
            contentIds = Set.copyOf(contentIds);
        }

        static @NotNull StackIdentity vanilla(@NotNull Material material) {
            return new StackIdentity(material, Set.of());
        }

        static @NotNull StackIdentity content(@NotNull Material material, @NotNull String key) {
            return new StackIdentity(material, Set.of(key));
        }
    }
}
