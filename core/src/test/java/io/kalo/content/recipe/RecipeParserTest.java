package io.kalo.content.recipe;

import io.kalo.content.recipe.definition.RecipeDefinition;
import io.kalo.content.recipe.definition.RecipeIngredient;
import io.kalo.content.recipe.definition.RecipeResult;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeParserTest {

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(content);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return config;
    }

    private static RecipeDefinition parse(String content) {
        YamlConfiguration config = yaml(content);
        return RecipeParser.parse(Key.key("mypack", "test"),
                java.util.Objects.requireNonNull(config.getConfigurationSection("recipe")));
    }

    @Test
    void aPatternMakesAShapedRecipe() {
        RecipeDefinition definition = parse("""
                recipe:
                  result: ruby_sword
                  pattern:
                    - " R "
                    - " R "
                    - " S "
                  ingredients:
                    R: mypack:ruby
                    S: minecraft:stick
                """);

        RecipeDefinition.Shaped shaped = assertInstanceOf(RecipeDefinition.Shaped.class, definition);
        assertEquals(List.of(" R ", " R ", " S "), shaped.pattern());
        assertInstanceOf(RecipeIngredient.Content.class, shaped.keys().get('R'));
        assertInstanceOf(RecipeIngredient.Vanilla.class, shaped.keys().get('S'));
    }

    @Test
    void noPatternMakesAShapelessRecipe() {
        RecipeDefinition definition = parse("""
                recipe:
                  result: ruby_dust
                  ingredients:
                    a: mypack:ruby
                    b: minecraft:flint
                """);

        RecipeDefinition.Shapeless shapeless =
                assertInstanceOf(RecipeDefinition.Shapeless.class, definition);
        assertEquals(2, shapeless.ingredients().size());
    }

    @Test
    void theNamespaceDecidesVanillaOrContentNotTheName() {
        // A pack is free to define mypack:diamond, and it must not resolve to the
        // vanilla one just because the name matches a material.
        assertInstanceOf(RecipeIngredient.Content.class,
                RecipeParser.parseIngredient("mypack", "diamond"));
        assertInstanceOf(RecipeIngredient.Content.class,
                RecipeParser.parseIngredient("mypack", "mypack:diamond"));
        assertInstanceOf(RecipeIngredient.Vanilla.class,
                RecipeParser.parseIngredient("mypack", "minecraft:diamond"));
    }

    @Test
    void anUnqualifiedNameMeansThisPack() {
        RecipeResult result = RecipeParser.parseResult("mypack", "ruby_sword");
        assertEquals(Key.key("mypack", "ruby_sword"), result.content());
    }

    @Test
    void anAmountPrefixIsUnderstood() {
        RecipeResult result = RecipeParser.parseResult("mypack", "4x ruby_dust");
        assertEquals(4, result.amount());
        assertEquals(Key.key("mypack", "ruby_dust"), result.content());
    }

    @Test
    void aPatternCharacterWithNoIngredientIsRejectedByName() {
        // The server's own error does not say which character was missing.
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parse("""
                recipe:
                  result: ruby_sword
                  pattern:
                    - " R "
                  ingredients:
                    S: minecraft:stick
                """));
        assertEquals(true, error.getMessage().contains("'R'"), error.getMessage());
    }

    @Test
    void anOversizedPatternIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeDefinition.Shaped(
                Key.key("mypack", "x"), RecipeResult.of(Key.key("mypack", "y")),
                List.of("RRRR"), Map.of('R', new RecipeIngredient.Vanilla(Key.key("minecraft", "stick")))));

        assertThrows(IllegalArgumentException.class, () -> new RecipeDefinition.Shaped(
                Key.key("mypack", "x"), RecipeResult.of(Key.key("mypack", "y")),
                List.of("R", "R", "R", "R"), Map.of('R', new RecipeIngredient.Vanilla(Key.key("minecraft", "stick")))));
    }

    @Test
    void aShapelessRecipeCannotExceedTheGrid() {
        List<RecipeIngredient> ten = java.util.Collections.nCopies(10,
                new RecipeIngredient.Vanilla(Key.key("minecraft", "stick")));

        assertThrows(IllegalArgumentException.class, () -> new RecipeDefinition.Shapeless(
                Key.key("mypack", "x"), RecipeResult.of(Key.key("mypack", "y")), ten));
    }

    @Test
    void aMissingIngredientsSectionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                recipe:
                  result: ruby_sword
                """));
    }

    @Test
    void aMultiCharacterIngredientKeyIsRejected() {
        // It could never match a single pattern slot.
        assertThrows(IllegalArgumentException.class, () -> parse("""
                recipe:
                  result: ruby_sword
                  pattern:
                    - " R "
                  ingredients:
                    RR: mypack:ruby
                """));
    }

    @Test
    void resultAmountsAreBounded() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeResult(Key.key("mypack", "x"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeResult(Key.key("mypack", "x"), 100));
    }
}
