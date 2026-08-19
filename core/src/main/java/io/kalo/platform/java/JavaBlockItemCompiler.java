package io.kalo.platform.java;

import io.kalo.content.block.Block;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.feature.event.ItemStackGenerationEvent;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Compiles a {@link BlockDefinition} into the item players hold and place.
 *
 * <p>The item uses Note Block as a stable placement token and carries the block's own
 * item-model component, so the thing in the hotbar matches the thing that appears in the
 * world. Virtual mode reuses this exact stack as its {@code ItemDisplay} payload.</p>
 */
public final class JavaBlockItemCompiler {

    /** PDC key stamped on the item form so placement can resolve which block it is. */
    public static final NamespacedKey BLOCK_ID_KEY = new NamespacedKey(Constants.PLUGIN_ID, "block");

    private JavaBlockItemCompiler() {
    }

    public static @NotNull ItemStack compile(@NotNull Block block) {
        BlockDefinition definition = block.definition();
        ItemStack itemStack = new ItemStack(Material.NOTE_BLOCK);

        itemStack.editMeta(meta -> {
            Component displayName = definition.display().name();
            if (displayName == null) {
                displayName = Component.translatable(block).color(NamedTextColor.WHITE);
            }
            if (displayName.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
                displayName = displayName.decoration(TextDecoration.ITALIC, false);
            }
            meta.displayName(displayName);

            if (!definition.display().lore().isEmpty()) {
                meta.lore(definition.display().lore());
            }

            Key key = definition.key();
            meta.setItemModel(new NamespacedKey(key.namespace(), key.value()));
            meta.getPersistentDataContainer().set(BLOCK_ID_KEY, PersistentDataType.STRING, key.asString());
        });

        block.featureEventBus().call(new ItemStackGenerationEvent(itemStack));

        return itemStack;
    }

    /** Reads the Kalo block id off an item, or {@code null} if it is not one of ours. */
    public static @Nullable String idOf(@NotNull ItemStack itemStack) {
        if (!itemStack.hasItemMeta()) {
            return null;
        }
        return Objects.requireNonNull(itemStack.getItemMeta())
                .getPersistentDataContainer()
                .get(BLOCK_ID_KEY, PersistentDataType.STRING);
    }
}
