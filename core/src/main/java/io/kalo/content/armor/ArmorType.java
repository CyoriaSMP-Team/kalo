package io.kalo.content.armor;

import io.kalo.config.ConfigSchema;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.item.ItemType;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.JavaPackCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class ArmorType implements ContentType<Armor> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "armor");
    private final ConfigSchema schema = new io.kalo.content.item.ItemConfigSchema();
    @Override public @NotNull String id() { return "armor"; }
    @Override public @NotNull Class<Armor> clazz() { return Armor.class; }
    @Override public @NotNull Iterable<Armor> contents(@NotNull Registries registries) { return registries.armor(); }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries, @NotNull ConfigurationSection config) {
        ConfigSchema.Result result = schema.validate(config);
        String slotName = config.getString("slot");
        if (slotName == null) result.failed("Missing armor slot (head, chest, legs or feet)");
        if (!result.isSuccess()) {
            Plugins.logger().warning("Failed to load armor '" + config.getName() + "' in pack '" + pack.namespace() + "'");
            result.getErrors().forEach(error -> Plugins.logger().warning("  " + error));
            return false;
        }
        Key key = pack.key(config.getName());
        try {
            ArmorSlot slot = ArmorSlot.valueOf(slotName.toUpperCase(Locale.ROOT));
            ItemDefinition item = ItemType.parseDefinition(key, config);
            List<FeatureBuilder> features = ItemType.parseFeatures(key, config.getConfigurationSection("features"));
            ArmorDefinition definition = new ArmorDefinition(item, slot);
            registries.armor().register(key, entry -> entry.key(key).definition(definition).features(features));
            return true;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Failed to load armor '" + key.asString() + "'", e);
            return false;
        }
    }

    @Override
    public void compilePack(@NotNull ResourcePack pack, @NotNull Iterable<Armor> contents) {
        List<io.kalo.content.item.Item> items = new ArrayList<>();
        contents.forEach(items::add);
        JavaPackCompiler.compileItems(pack, items);
    }
}
