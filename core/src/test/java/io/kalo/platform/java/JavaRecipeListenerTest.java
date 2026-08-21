package io.kalo.platform.java;

import io.kalo.content.recipe.definition.RecipeDefinition;
import io.kalo.content.recipe.definition.RecipeIngredient;
import io.kalo.content.recipe.definition.RecipeResult;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaRecipeListenerTest {

    private static final RecipeResult RESULT = RecipeResult.of(Key.key("testpack", "result"));

    private static RecipeIngredient vanilla(String value) {
        return new RecipeIngredient.Vanilla(Key.key("minecraft", value));
    }

    private static RecipeIngredient content(String value) {
        return new RecipeIngredient.Content(Key.key("testpack", value));
    }

    @Test
    void shapedRecipesMatchAtOffsetsAndMirrored() {
        RecipeDefinition recipe = new RecipeDefinition.Shaped(
                Key.key("testpack", "two"), RESULT, List.of("AB"),
                Map.of('A', vanilla("stick"), 'B', vanilla("diamond")));

        JavaRecipeListener.StackIdentity[] offset = new JavaRecipeListener.StackIdentity[9];
        offset[4] = JavaRecipeListener.StackIdentity.vanilla(Material.STICK);
        offset[5] = JavaRecipeListener.StackIdentity.vanilla(Material.DIAMOND);
        assertTrue(JavaRecipeListener.matches(recipe, offset));

        JavaRecipeListener.StackIdentity[] mirrored = new JavaRecipeListener.StackIdentity[9];
        mirrored[3] = JavaRecipeListener.StackIdentity.vanilla(Material.DIAMOND);
        mirrored[4] = JavaRecipeListener.StackIdentity.vanilla(Material.STICK);
        assertTrue(JavaRecipeListener.matches(recipe, mirrored));

        mirrored[8] = JavaRecipeListener.StackIdentity.vanilla(Material.STICK);
        assertFalse(JavaRecipeListener.matches(recipe, mirrored), "extra grid items must not be ignored");
    }

    @Test
    void shapelessMatchingBacktracksAcrossVanillaAndCustomCarrierAmbiguity() {
        RecipeDefinition recipe = new RecipeDefinition.Shapeless(
                Key.key("testpack", "mixed"), RESULT,
                List.of(vanilla("paper"), content("ruby")));
        JavaRecipeListener.StackIdentity[] matrix = new JavaRecipeListener.StackIdentity[9];
        // The custom PAPER appears first. A greedy matcher would consume it for the
        // vanilla ingredient and then fail to find ruby in the remaining plain PAPER.
        matrix[0] = JavaRecipeListener.StackIdentity.content(Material.PAPER, "testpack:ruby");
        matrix[1] = JavaRecipeListener.StackIdentity.vanilla(Material.PAPER);

        assertTrue(JavaRecipeListener.matches(recipe, matrix));
    }

    @Test
    void customIdentityIgnoresRenamesButRejectsPlainCarrierItems() {
        RecipeDefinition recipe = new RecipeDefinition.Shapeless(
                Key.key("testpack", "ruby_only"), RESULT, List.of(content("ruby")));
        JavaRecipeListener.StackIdentity[] renamed = new JavaRecipeListener.StackIdentity[9];
        // StackIdentity deliberately has no name/lore/damage fields: only the persistent
        // id survives an anvil rename and participates in matching.
        renamed[0] = JavaRecipeListener.StackIdentity.content(Material.PAPER, "testpack:ruby");
        assertTrue(JavaRecipeListener.matches(recipe, renamed));

        JavaRecipeListener.StackIdentity[] plain = new JavaRecipeListener.StackIdentity[9];
        plain[0] = JavaRecipeListener.StackIdentity.vanilla(Material.PAPER);
        assertFalse(JavaRecipeListener.matches(recipe, plain));
    }

    @Test
    void persistentIdsResolveRecipesThatShareTheSameCarrierPattern() {
        RecipeDefinition ruby = new RecipeDefinition.Shapeless(
                Key.key("testpack", "z_ruby"), RESULT, List.of(content("ruby")));
        RecipeDefinition sapphire = new RecipeDefinition.Shapeless(
                Key.key("testpack", "a_sapphire"), RESULT, List.of(content("sapphire")));
        JavaRecipeListener.StackIdentity[] matrix = new JavaRecipeListener.StackIdentity[9];
        matrix[0] = JavaRecipeListener.StackIdentity.content(Material.PAPER, "testpack:sapphire");

        assertEquals(sapphire, JavaRecipeListener.matching(List.of(ruby, sapphire), matrix));
        assertFalse(JavaRecipeListener.matches(ruby, matrix));
        assertTrue(JavaRecipeListener.matches(sapphire, matrix));
    }

    @Test
    void trulyDuplicateInputsHaveADeterministicKeyOrderedWinner() {
        RecipeDefinition later = new RecipeDefinition.Shapeless(
                Key.key("testpack", "z_later"), RESULT, List.of(content("ruby")));
        RecipeDefinition first = new RecipeDefinition.Shapeless(
                Key.key("testpack", "a_first"), RESULT, List.of(content("ruby")));
        JavaRecipeListener.StackIdentity[] matrix = new JavaRecipeListener.StackIdentity[9];
        matrix[0] = JavaRecipeListener.StackIdentity.content(Material.PAPER, "testpack:ruby");

        assertEquals(first, JavaRecipeListener.matching(List.of(later, first), matrix));
    }
}
