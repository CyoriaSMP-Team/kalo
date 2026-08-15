package io.kalo.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeImportTest {

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(content);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return config;
    }

    @Test
    void underscoresBecomeSpaces() {
        // Both plugins mark an empty slot with _. Left alone it would look like an
        // ingredient character with nothing bound to it.
        assertEquals(List.of(" R ", " S "), RecipeImport.normalisePattern(List.of("_R_", "_S_")));
    }

    @Test
    void raggedRowsArePaddedToARectangle() {
        // Both plugins tolerate this; Bukkit does not, and its complaint does not say
        // which row is short.
        assertEquals(List.of("RR ", "  S"), RecipeImport.normalisePattern(List.of("RR", "  S")));
    }

    @Test
    void vanillaMaterialsGetTheMinecraftNamespace() {
        assertEquals("minecraft:stick", RecipeImport.vanilla("STICK"));
        assertEquals("minecraft:stick", RecipeImport.vanilla("minecraft:STICK"));
    }

    @Test
    void anOraxenShapedRecipeConverts() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = yaml(OraxenImporter.convertRecipes(yaml("""
                ruby_sword_recipe:
                  shape:
                    - "_R_"
                    - "_R_"
                    - "_S_"
                  ingredients:
                    R:
                      oraxen_item: ruby
                    S:
                      minecraft_type: STICK
                  result:
                    oraxen_item: ruby_sword
                    amount: 1
                """), "mypack", report));

        assertEquals("recipe", out.getString("ruby_sword_recipe.type"));
        assertEquals("ruby_sword", out.getString("ruby_sword_recipe.result"));
        assertEquals(List.of(" R ", " R ", " S "), out.getStringList("ruby_sword_recipe.pattern"));
        // oraxen_item stays unqualified, which Kalo reads as "in this pack".
        assertEquals("ruby", out.getString("ruby_sword_recipe.ingredients.R"));
        assertEquals("minecraft:stick", out.getString("ruby_sword_recipe.ingredients.S"));
    }

    @Test
    void anAmountAboveOneBecomesThePrefixForm() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = yaml(OraxenImporter.convertRecipes(yaml("""
                dust:
                  ingredients:
                    R:
                      oraxen_item: ruby
                  result:
                    oraxen_item: ruby_dust
                    amount: 4
                """), "mypack", report));

        assertEquals("4x ruby_dust", out.getString("dust.result"));
    }

    @Test
    void aVanillaResultIsReportedBecauseKaloRecipesProduceKaloContent() {
        ImportReport report = new ImportReport();
        OraxenImporter.convertRecipes(yaml("""
                sticks:
                  ingredients:
                    R:
                      oraxen_item: ruby
                  result:
                    minecraft_type: STICK
                """), "mypack", report);

        assertTrue(report.unsupported().stream().anyMatch(p -> p.contains("minecraft_type")),
                report.unsupported().toString());
    }

    @Test
    void anItemsAdderCraftingTableRecipeConverts() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = yaml(ItemsAdderImporter.convert(yaml("""
                info:
                  namespace: mypack
                recipes:
                  crafting_table:
                    ruby_sword_recipe:
                      pattern:
                        - "_R_"
                        - "_R_"
                        - "_S_"
                      ingredients:
                        R:
                          item: mypack:ruby
                        S:
                          item: minecraft:stick
                      result:
                        item: mypack:ruby_sword
                        amount: 1
                """), report));

        assertEquals("recipe", out.getString("ruby_sword_recipe.type"));
        assertEquals("mypack:ruby_sword", out.getString("ruby_sword_recipe.result"));
        assertEquals("mypack:ruby", out.getString("ruby_sword_recipe.ingredients.R"));
    }

    @Test
    void nonCraftingTableStationsAreReportedNotSilentlyReshaped() {
        // A furnace recipe is not a crafting recipe; importing it as one would be wrong
        // in a way that looks right.
        ImportReport report = new ImportReport();
        ItemsAdderImporter.convert(yaml("""
                info:
                  namespace: mypack
                recipes:
                  furnace:
                    smelt_ruby:
                      result:
                        item: mypack:ruby
                """), report);

        assertTrue(report.unsupported().stream().anyMatch(p -> p.contains("recipes.furnace")),
                report.unsupported().toString());
    }

    @Test
    void aRecipeWithNoReadableIngredientsFailsThatRecipeAlone() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = yaml(OraxenImporter.convertRecipes(yaml("""
                broken:
                  ingredients:
                    R:
                      something_unknown: x
                  result:
                    oraxen_item: ruby
                good:
                  ingredients:
                    R:
                      oraxen_item: ruby
                  result:
                    oraxen_item: ruby_dust
                """), "mypack", report));

        assertEquals(1, report.failed().size());
        assertEquals("recipe", out.getString("good.type"));
    }
}
