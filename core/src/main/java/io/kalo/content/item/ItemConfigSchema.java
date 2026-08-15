package io.kalo.content.item;

import io.kalo.config.ConfigSchema;
import org.bukkit.configuration.ConfigurationSection;

public final class ItemConfigSchema implements ConfigSchema {

    @Override
    public Result validate(ConfigurationSection config) {
        Result result = new Result();

        ConfigurationSection model = config.getConfigurationSection("model");
        if (model != null) {
            boolean hasSource = model.contains("sprite") || model.contains("vanilla") || model.contains("custom");
            if (!hasSource) {
                result.failed("model section must declare one of 'sprite', 'vanilla' or 'custom'");
            }
            if (model.contains("textures") && !model.contains("custom")) {
                result.failed("'textures' only applies to a 'custom' model");
            }
        }

        ConfigurationSection behaviour = config.getConfigurationSection("behaviour");
        if (behaviour == null) {
            behaviour = config.getConfigurationSection("behavior");
        }
        if (behaviour != null) {
            if (behaviour.contains("stack_size")) {
                int stackSize = behaviour.getInt("stack_size");
                if (stackSize < 1 || stackSize > 99) {
                    result.failed("stack_size must be within 1..99, got " + stackSize);
                }
            }
            if (behaviour.contains("durability") && behaviour.getInt("durability") < 1) {
                result.failed("durability must be positive");
            }
        }

        return result;
    }
}
