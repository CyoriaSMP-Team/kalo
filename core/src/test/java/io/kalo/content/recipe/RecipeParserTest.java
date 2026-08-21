package io.kalo.content.recipe;

import io.kalo.content.recipe.definition.RecipeDefinition;
import io.kalo.content.recipe.definition.RecipeIngredient;
import io.kalo.content.recipe.definition.RecipeResult;
import io.kalo.content.PackContext;
import io.kalo.registry.RegistriesImpl;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.io.File;

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
    void shapedRecipesMustBeRectangularAndContainAnIngredient() {
        RecipeIngredient stick = new RecipeIngredient.Vanilla(Key.key("minecraft", "stick"));

        assertThrows(IllegalArgumentException.class, () -> new RecipeDefinition.Shaped(
                Key.key("mypack", "ragged"), RecipeResult.of(Key.key("mypack", "result")),
                List.of("RR", "R"), Map.of('R', stick)));
        assertThrows(IllegalArgumentException.class, () -> new RecipeDefinition.Shaped(
                Key.key("mypack", "empty"), RecipeResult.of(Key.key("mypack", "result")),
                List.of("   "), Map.of()));
    }

    @Test
    void unusedIngredientKeysAreRejectedAtParseTime() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parse("""
                recipe:
                  result: ruby_sword
                  pattern:
                    - " R "
                  ingredients:
                    R: mypack:ruby
                    S: minecraft:stick
                """));
        assertEquals(true, error.getMessage().contains("never used"), error.getMessage());
    }

    @Test
    void vanillaIngredientsCannotSmuggleInAnotherNamespace() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeIngredient.Vanilla(Key.key("mypack", "stick")));
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

    @Test
    void duplicateRecipeKeysKeepTheFirstDefinition() {
        RecipeType type = new RecipeType();
        PackContext pack = new PackContext("mypack", new File("."));
        RegistriesImpl registries = new RegistriesImpl();
        ConfigurationSection first = java.util.Objects.requireNonNull(yaml("""
                same:
                  result: ruby
                  ingredients:
                    a: minecraft:stick
                """).getConfigurationSection("same"));
        ConfigurationSection second = java.util.Objects.requireNonNull(yaml("""
                same:
                  result: sapphire
                  ingredients:
                    a: minecraft:diamond
                """).getConfigurationSection("same"));

        assertEquals(true, type.load(pack, registries, first));
        assertEquals(false, type.load(pack, registries, second));
        assertEquals(1, type.size());
    }

    @Test
    void furnaceStationMakesCookingRecipe() {
        RecipeDefinition definition = parse("""
                recipe:
                  result: mypack:ruby
                  station: furnace
                  input: mypack:ruby_ore
                  experience: 0.7
                  cooking_time: 200
                """);
        RecipeDefinition.Cooking cooking = assertInstanceOf(RecipeDefinition.Cooking.class, definition);
        assertEquals(RecipeDefinition.CookingStation.FURNACE, cooking.station());
        assertInstanceOf(RecipeIngredient.Content.class, cooking.input());
    }

    @Test
    void stonecutterStationMakesStonecuttingRecipe() {
        RecipeDefinition definition = parse("""
                recipe:
                  result: 4x mypack:ruby
                  station: stonecutter
                  input: mypack:ruby_block
                """);
        RecipeDefinition.Stonecutting cutting = assertInstanceOf(RecipeDefinition.Stonecutting.class, definition);
        assertEquals(4, cutting.result().amount());
    }

    @Test
    void smithingStationMakesSmithingRecipe() {
        RecipeDefinition definition = parse("""
                recipe:
                  result: mypack:ruby_sword
                  station: smithing
                  base: minecraft:netherite_sword
                  addition: mypack:ruby
                """);
        RecipeDefinition.Smithing smithing = assertInstanceOf(RecipeDefinition.Smithing.class, definition);
        assertInstanceOf(RecipeIngredient.Vanilla.class, smithing.base());
        assertInstanceOf(RecipeIngredient.Content.class, smithing.addition());
    }
}
