package io.kalo.platform.java;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Testable Java-edition behavior rules for Kalo's solid blocks. */
final class JavaBlockRules {

    private JavaBlockRules() {
    }

    /** Kalo's current solid-block intent follows stone: a pickaxe is the correct tool. */
    static boolean isCorrectTool(@NotNull Material material) {
        // Name-based rather than a fixed six-value switch so newly added tool tiers such
        // as copper automatically participate without changing persisted content.
        return material.name().endsWith("_PICKAXE");
    }

    static boolean preventsTuning(@NotNull Material material, @NotNull Action action) {
        return material == Material.NOTE_BLOCK && action == Action.RIGHT_CLICK_BLOCK;
    }

    static @Nullable Key contentKey(@NotNull String value) {
        try {
            return Key.key(value);
        } catch (InvalidKeyException ignored) {
            return null;
        }
    }
}
