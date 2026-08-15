package io.kalo.content.armor;

import io.kalo.config.ConfigSchema;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.Item;
import io.kalo.content.item.ItemConfigSchema;
import io.kalo.content.item.ItemType;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.JavaArmorCompiler;
import io.kalo.platform.java.JavaPackCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class ArmorType implements ContentType<Armor> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "armor");

    private final ConfigSchema schema = new ItemConfigSchema();

    @Override
    public @NotNull String id() {
        return "armor";
    }

    @Override
    public @NotNull Class<Armor> clazz() {
        return Armor.class;
    }

    @Override
    public @NotNull Iterable<Armor> contents(@NotNull Registries registries) {
        return registries.armor();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries, @NotNull ConfigurationSection config) {
        ConfigSchema.Result result = schema.validate(config);

        String slotName = config.getString("slot");
        if (slotName == null) {
            result.failed("Missing armor slot (head, chest, legs or feet)");
        } else if (!isKnownSlot(slotName)) {
            result.failed("Unknown armor slot '" + slotName + "' — expected head, chest, legs or feet");
        }

        if (!result.isSuccess()) {
            Plugins.logger().warning("Failed to load armor '" + config.getName() + "' in pack '" + pack.namespace() + "'");
            result.getErrors().forEach(error -> Plugins.logger().warning("  " + error));
            return false;
        }

        Key key = pack.key(config.getName());
        try {
            ArmorSlot slot = ArmorSlot.valueOf(slotName.toUpperCase(Locale.ROOT));
            ItemDefinition item = ItemType.parseDefinition(key, config);
            ArmorDefinition.EquipmentTexture equipment =
                    parseEquipment(key, slot, config.getConfigurationSection("equipment"));
            List<FeatureBuilder> features = ItemType.parseFeatures(key, config.getConfigurationSection("features"));

            ArmorDefinition definition = new ArmorDefinition(item, slot, equipment);
            registries.armor().register(key, entry -> entry.key(key).definition(definition).features(features));
            return true;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Failed to load armor '" + key.asString() + "': " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Parses the worn appearance.
     *
     * <p>Defaults to a texture named after the piece, because that is what a pack author
     * almost always wants and it keeps the common case free of boilerplate. An explicit
     * {@code equipment: {enabled: false}} opts out and keeps the base material's vanilla
     * armor texture.</p>
     */
    private static @Nullable ArmorDefinition.EquipmentTexture parseEquipment(
            @NotNull Key key, @NotNull ArmorSlot slot, @Nullable ConfigurationSection config) {

        if (config != null && !config.getBoolean("enabled", true)) {
            return null;
        }

        String humanoid = config != null ? config.getString("humanoid") : null;
        String leggings = config != null ? config.getString("leggings") : null;

        Key humanoidKey = humanoid != null ? resolveKey(key.namespace(), humanoid) : key;
        Key leggingsKey = leggings != null
                ? resolveKey(key.namespace(), leggings)
                : (slot.usesLeggingsLayer() ? humanoidKey : null);

        return new ArmorDefinition.EquipmentTexture(humanoidKey, leggingsKey);
    }

    @Override
    public void compilePack(@NotNull ResourcePack pack, @NotNull Iterable<Armor> contents) {
        List<Item> items = new ArrayList<>();
        contents.forEach(items::add);

        // The icon, like any other item...
        JavaPackCompiler.compileItems(pack, items);
        // ...plus the equipment asset that paints it onto the player.
        JavaArmorCompiler.compileArmor(pack, contents);
    }

    private static boolean isKnownSlot(@NotNull String name) {
        for (ArmorSlot slot : ArmorSlot.values()) {
            if (slot.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static @NotNull Key resolveKey(@NotNull String fallbackNamespace, @NotNull String value) {
        int separator = value.indexOf(':');
        if (separator < 0) {
            return Key.key(fallbackNamespace, value);
        }
        return Key.key(value.substring(0, separator), value.substring(separator + 1));
    }
}
