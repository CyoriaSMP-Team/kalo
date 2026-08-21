package io.kalo.platform.java;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaBlockListenerTest {

    @Test
    void requiredToolUsesTheDefinitionsStoneLikePickaxeSemantics() {
        assertTrue(JavaBlockRules.isCorrectTool(Material.WOODEN_PICKAXE));
        assertTrue(JavaBlockRules.isCorrectTool(Material.DIAMOND_PICKAXE));
        assertTrue(JavaBlockRules.isCorrectTool(Material.NETHERITE_PICKAXE));
        assertFalse(JavaBlockRules.isCorrectTool(Material.DIAMOND_AXE));
        assertFalse(JavaBlockRules.isCorrectTool(Material.AIR));

        Material copper = Material.matchMaterial("COPPER_PICKAXE");
        if (copper != null) {
            assertTrue(JavaBlockRules.isCorrectTool(copper));
        }
    }

    @Test
    void tuningProtectionOnlyCancelsRightClicksOnNoteBlocks() {
        // This rule was correct and tested from the start. What it lacked was a caller:
        // onInteract cancelled the whole PlayerInteractEvent for any carrier and any
        // action, so left-click never reached the dig, and no native custom block could be
        // broken. A rule nothing consults protects nothing.
        assertTrue(JavaBlockRules.preventsTuning(Material.NOTE_BLOCK, Action.RIGHT_CLICK_BLOCK));
        assertFalse(JavaBlockRules.preventsTuning(Material.NOTE_BLOCK, Action.LEFT_CLICK_BLOCK));
        assertFalse(JavaBlockRules.preventsTuning(Material.NOTE_BLOCK, Action.PHYSICAL));
        assertFalse(JavaBlockRules.preventsTuning(Material.STONE, Action.RIGHT_CLICK_BLOCK));
    }

    @Test
    void corruptStampedBlockIdsAreRejectedWithoutThrowing() {
        assertEquals(Key.key("testpack", "ruby"), JavaBlockRules.contentKey("testpack:ruby"));
        assertNull(JavaBlockRules.contentKey("not a valid key"));
        assertNull(JavaBlockRules.contentKey("UPPER:CASE"));
    }
}
