package io.kalo.content.item;

import io.kalo.config.ConfigSchema;
import org.bukkit.configuration.ConfigurationSection;

public final class ItemConfigSchema implements ConfigSchema {

    @Override
    public Result validate(ConfigurationSection config) {
        Result result = new Result();

        ConfigurationSection model = config.getConfigurationSection("model");
        if (config.contains("model") && model == null) {
            result.failed("model must be a section");
        } else if (model != null) {
            int sources = (model.contains("sprite") ? 1 : 0)
                    + (model.contains("vanilla") ? 1 : 0)
                    + (model.contains("custom") ? 1 : 0);
            if (sources == 0) {
                result.failed("model section must declare one of 'sprite', 'vanilla' or 'custom'");
            } else if (sources > 1) {
                result.failed("model section must declare exactly one of 'sprite', 'vanilla' or 'custom'");
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
