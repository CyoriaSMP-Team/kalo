package io.kalo.content.gui;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;

/**
 * Platform-neutral description of a custom GUI (inventory menu).
 *
 * @param key          content key (namespace:name)
 * @param title        GUI title (supports MiniMessage)
 * @param rows         inventory rows (1-6)
 * @param items        slot configurations
 * @param closeActions actions when GUI is closed
 */
public record GuiDefinition(
        @NotNull Key key,
        @NotNull String title,
        int rows,
        @NotNull @Unmodifiable List<SlotConfig> items,
        @Nullable List<String> closeActions
) {
    public GuiDefinition {
        items = List.copyOf(items);
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1-6, got " + rows);
        }
    }

    /**
     * Configuration for a single GUI slot.
     *
     * @param slot         slot number (0-based)
     * @param material     item material or custom item key
     * @param displayName  item display name (MiniMessage)
     * @param lore         item lore lines
     * @param amount       item stack amount
     * @param customModelData  custom model data value
     * @param actions      click actions (commands, close, etc.)
     * @param conditions   conditional display rules
     */
    public record SlotConfig(
            int slot,
            @NotNull String material,
            @Nullable String displayName,
            @NotNull List<String> lore,
            int amount,
            int customModelData,
            @NotNull List<String> actions,
            @NotNull Map<String, String> conditions
    ) {
        public SlotConfig {
            lore = List.copyOf(lore);
            actions = List.copyOf(actions);
            conditions = Map.copyOf(conditions);
        }
    }

    public static @NotNull Builder builder(@NotNull Key key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final Key key;
        private String title = "Menu";
        private int rows = 6;
        private List<SlotConfig> items = List.of();
        private List<String> closeActions = List.of();

        private Builder(@NotNull Key key) {
            this.key = key;
        }

        public @NotNull Builder title(@NotNull String title) { this.title = title; return this; }
        public @NotNull Builder rows(int rows) { this.rows = rows; return this; }
        public @NotNull Builder items(@NotNull List<SlotConfig> items) { this.items = items; return this; }
        public @NotNull Builder closeActions(@NotNull List<String> closeActions) { this.closeActions = closeActions; return this; }

        public @NotNull GuiDefinition build() {
            return new GuiDefinition(key, title, rows, items, closeActions);
        }
    }
}
