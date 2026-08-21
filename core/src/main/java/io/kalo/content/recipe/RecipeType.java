package io.kalo.content.recipe;

import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.recipe.definition.RecipeDefinition;
import io.kalo.platform.java.JavaRecipeCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads recipes from pack YAML.
 *
 * <p>Recipes are not {@code Content}: they have no key of their own to give a player, no
 * item form and nothing in the resource pack. They are held here rather than in the
 * content registries, and registered with the server once loading has finished — a recipe
 * can reference content from a pack that has not been read yet, so resolution has to wait
 * until every pack is in.</p>
 */
public final class RecipeType implements ContentType<io.kalo.content.item.Item> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "recipe");
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(RecipeType.class.getName());

    /** Keyed so a reload replaces rather than accumulates. */
    private final Map<Key, RecipeDefinition> recipes = new ConcurrentHashMap<>();

    @Override
    public @NotNull String id() {
        return "recipe";
    }

    @Override
    public @NotNull Class<io.kalo.content.item.Item> clazz() {
        return io.kalo.content.item.Item.class;
    }

    @Override
    public @NotNull Iterable<io.kalo.content.item.Item> contents(@NotNull Registries registries) {
        // Recipes contribute nothing to the resource pack, so there is nothing to walk.
        return List.of();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries,
                        @NotNull ConfigurationSection config) {
        Key key = pack.key(config.getName());
        try {
            RecipeDefinition definition = RecipeParser.parse(key, config);
            JavaRecipeCompiler.validate(definition);
            if (recipes.putIfAbsent(key, definition) != null) {
                LOGGER.warning("Duplicate recipe '" + key.asString() + "'; keeping the first definition");
                return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to load recipe '" + key.asString() + "': " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Registers every loaded recipe with the server.
     *
     * <p>Deferred until all packs are loaded so a recipe may reference content defined in
     * another pack, in any order.</p>
     */
    public int registerAll() {
        return JavaRecipeCompiler.register(new ArrayList<>(recipes.values()));
    }

    /** Removes registered recipes and forgets them, so a reload starts clean. */
    public void clear() {
        JavaRecipeCompiler.unregisterAll(new ArrayList<>(recipes.values()));
        recipes.clear();
    }

    public int size() {
        return recipes.size();
    }
}
