package io.kalo.platform.java;

import io.kalo.content.item.Item;
import io.kalo.content.recipe.definition.RecipeDefinition;
import io.kalo.content.recipe.definition.RecipeIngredient;
import io.kalo.content.recipe.definition.RecipeResult;
import io.kalo.manager.RegistryManager;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers Kalo recipes with the server.
 *
 * <p>Matching a Kalo ingredient is the interesting part. Bukkit's exact-choice matching
 * compares whole item stacks, and a Kalo item carries a display name and lore that a
 * player could plausibly have changed on an anvil — so an exact match would reject a
 * legitimately-obtained item. Kalo items already carry their id in persistent data, so
 * the recipe matches on a freshly built copy, which is stable regardless of what the
 * player has renamed.</p>
 */
public final class JavaRecipeCompiler {
    private static final Logger LOGGER = Logger.getLogger(JavaRecipeCompiler.class.getName());

    private JavaRecipeCompiler() {
    }

    /**
     * Registers every recipe, replacing any previously registered under the same key so a
     * reload does not fail on "recipe already exists".
     */
    public static int register(@NotNull Iterable<RecipeDefinition> recipes) {
        int registered = 0;

        for (RecipeDefinition definition : recipes) {
            NamespacedKey key = toNamespacedKey(definition.key());
            try {
                ItemStack result = resultStack(definition.result());
                if (result == null) {
                    LOGGER.warning("Recipe " + definition.key().asString() + " produces unknown content "
                            + definition.result().content().asString());
                    continue;
                }

                // Removing first makes reload idempotent; Bukkit throws on a duplicate key.
                Bukkit.removeRecipe(key);

                // Same situation as an unknown result, and it should read the same way:
                // one item failing to load should not spray stack traces for every recipe
                // that mentions it.
                String missing = firstMissingIngredient(definition);
                if (missing != null) {
                    LOGGER.warning("Recipe " + definition.key().asString()
                            + " needs unknown content " + missing);
                    continue;
                }

                switch (definition) {
                    case RecipeDefinition.Shaped shaped -> Bukkit.addRecipe(shaped(key, result, shaped));
                    case RecipeDefinition.Shapeless shapeless ->
                            Bukkit.addRecipe(shapeless(key, result, shapeless));
                }
                registered++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to register recipe " + definition.key().asString(), e);
            }
        }

        return registered;
    }

    /** Removes every recipe under Kalo's namespace, so a reload starts from a clean slate. */
    public static void unregisterAll(@NotNull Iterable<RecipeDefinition> recipes) {
        for (RecipeDefinition definition : recipes) {
            Bukkit.removeRecipe(toNamespacedKey(definition.key()));
        }
    }

    /** @return the first ingredient that cannot be resolved, or null when all can */
    private static @Nullable String firstMissingIngredient(@NotNull RecipeDefinition definition) {
        Iterable<RecipeIngredient> ingredients = switch (definition) {
            case RecipeDefinition.Shaped shaped -> shaped.keys().values();
            case RecipeDefinition.Shapeless shapeless -> shapeless.ingredients();
        };

        for (RecipeIngredient ingredient : ingredients) {
            if (choice(ingredient) == null) {
                return switch (ingredient) {
                    case RecipeIngredient.Content content -> content.key().asString();
                    case RecipeIngredient.Vanilla vanilla -> vanilla.item().asString();
                };
            }
        }
        return null;
    }

    private static @NotNull ShapedRecipe shaped(@NotNull NamespacedKey key,
                                                @NotNull ItemStack result,
                                                @NotNull RecipeDefinition.Shaped definition) {
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(definition.pattern().toArray(String[]::new));

        for (Map.Entry<Character, RecipeIngredient> entry : definition.keys().entrySet()) {
            RecipeChoice choice = choice(entry.getValue());
            if (choice == null) {
                throw new IllegalArgumentException("unknown ingredient for '" + entry.getKey() + "'");
            }
            recipe.setIngredient(entry.getKey(), choice);
        }
        return recipe;
    }

    private static @NotNull ShapelessRecipe shapeless(@NotNull NamespacedKey key,
                                                      @NotNull ItemStack result,
                                                      @NotNull RecipeDefinition.Shapeless definition) {
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (RecipeIngredient ingredient : definition.ingredients()) {
            RecipeChoice choice = choice(ingredient);
            if (choice == null) {
                throw new IllegalArgumentException("unknown ingredient " + ingredient);
            }
            recipe.addIngredient(choice);
        }
        return recipe;
    }

    private static @Nullable RecipeChoice choice(@NotNull RecipeIngredient ingredient) {
        return switch (ingredient) {
            case RecipeIngredient.Vanilla vanilla -> {
                Material material = Material.matchMaterial(vanilla.item().value().toUpperCase(Locale.ROOT));
                yield material != null ? new RecipeChoice.MaterialChoice(material) : null;
            }
            case RecipeIngredient.Content content -> {
                ItemStack stack = contentStack(content.key());
                // ExactChoice compares the whole stack. That is the point for Kalo items:
                // a plain PAPER must not satisfy a slot that wants mypack:ruby.
                yield stack != null ? new RecipeChoice.ExactChoice(stack) : null;
            }
        };
    }

    private static @Nullable ItemStack resultStack(@NotNull RecipeResult result) {
        ItemStack stack = contentStack(result.content());
        if (stack == null) {
            return null;
        }
        stack.setAmount(result.amount());
        return stack;
    }

    /** Looks a content key up across every type that has an item form. */
    private static @Nullable ItemStack contentStack(@NotNull Key key) {
        RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();

        Item item = registries.item().get(key).orElse(null);
        if (item != null) {
            return item.itemStack().get();
        }
        return registries.block().get(key)
                .map(block -> block.itemStack().get())
                .orElseGet(() -> registries.furniture().get(key)
                        .map(furniture -> furniture.itemStack().get())
                        .orElse(null));
    }

    private static @NotNull NamespacedKey toNamespacedKey(@NotNull Key key) {
        return new NamespacedKey(key.namespace(), key.value());
    }

    /** Exposed so the content type can validate a pattern before the server sees it. */
    public static void validate(@NotNull RecipeDefinition definition) {
        if (definition instanceof RecipeDefinition.Shaped shaped) {
            List<String> pattern = shaped.pattern();
            int width = pattern.get(0).length();
            for (String row : pattern) {
                if (row.length() != width) {
                    // Bukkit requires a rectangular shape and its own error does not say
                    // which row is the odd one out.
                    throw new IllegalArgumentException(
                            "every pattern row must be the same length; '" + row + "' is " + row.length()
                                    + " but the first row is " + width);
                }
            }
        }
    }
}
