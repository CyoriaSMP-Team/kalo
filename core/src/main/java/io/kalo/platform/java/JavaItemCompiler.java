package io.kalo.platform.java;

import io.kalo.content.feature.event.ItemStackGenerationEvent;
import io.kalo.content.item.Item;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Compiles a platform-neutral {@link ItemDefinition} into a Java-platform {@link ItemStack}.
 *
 * <p>This is where {@code Material} and every other Bukkit concept enters the pipeline.
 * A Bedrock compiler consumes the same definition and never sees any of it.</p>
 */
public final class JavaItemCompiler {

    /** PDC key stamped on every generated stack so items survive a restart. */
    public static final NamespacedKey ITEM_ID_KEY = new NamespacedKey(Constants.PLUGIN_ID, "item");

    private JavaItemCompiler() {
    }

    public static @NotNull ItemStack compile(@NotNull Item item) {
        ItemDefinition definition = item.definition();
        ItemStack itemStack = new ItemStack(definition.java().baseMaterial());

        itemStack.editMeta(meta -> {
            Component displayName = definition.display().name();
            if (displayName == null) {
                displayName = Component.translatable(item).color(NamedTextColor.WHITE);
            }
            // Vanilla italicises anything with a custom name; opt out unless the pack
            // author asked for italics explicitly.
            if (displayName.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
                displayName = displayName.decoration(TextDecoration.ITALIC, false);
            }
            meta.displayName(displayName);

            if (!definition.display().lore().isEmpty()) {
                meta.lore(definition.display().lore());
            }
            if (definition.display().enchantmentGlint()) {
                meta.setEnchantmentGlintOverride(true);
            }

            meta.setMaxStackSize(definition.behaviour().maxStackSize());
            if (definition.behaviour().maxDurability() != null && meta instanceof Damageable damageable) {
                damageable.setMaxDamage(definition.behaviour().maxDurability());
            }
            if (definition.behaviour().fireResistant()) {
                // Superseded by damage-type tags, but Paper exposes no Tag constant for
                // minecraft:is_fire, so this stays until there is a non-reflective path.
                meta.setFireResistant(true);
            }

            // Points the client at the item definition emitted by JavaPackCompiler.
            meta.setItemModel(toNamespacedKey(modelKey(definition)));

            meta.getPersistentDataContainer().set(ITEM_ID_KEY, PersistentDataType.STRING, item.key().asString());
        });

        item.featureEventBus().call(new ItemStackGenerationEvent(itemStack));

        return itemStack;
    }

    /**
     * The item definition key the client resolves the model through. A {@code Vanilla}
     * model reuses the vanilla definition directly instead of emitting a duplicate.
     */
    static @NotNull Key modelKey(@NotNull ItemDefinition definition) {
        if (definition.model() instanceof ModelDefinition.Vanilla vanilla) {
            return vanilla.item();
        }
        return definition.key();
    }

    private static @NotNull NamespacedKey toNamespacedKey(@NotNull Key key) {
        return new NamespacedKey(key.namespace(), key.value());
    }
}
