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
                    + (model.contains("custom") ? 1 : 0)
                    + (model.contains("blockbench") ? 1 : 0);
            if (sources == 0) {
                result.failed("model section must declare one of 'sprite', 'vanilla',"
                        + " 'custom' or 'blockbench'");
            } else if (sources > 1) {
                result.failed("model section must declare exactly one of 'sprite',"
                        + " 'vanilla', 'custom' or 'blockbench'");
            }
            if (model.contains("textures")
                    && !model.contains("custom") && !model.contains("blockbench")) {
                result.failed("'textures' only applies to a 'custom' or 'blockbench' model");
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

        ConfigurationSection place = config.getConfigurationSection("place");
        if (config.contains("place") && place == null) {
            result.failed("place must be a section");
        } else if (place != null) {
            if (place.getDouble("scale", 1.0) <= 0) {
                result.failed("place.scale must be positive");
            }
            if (place.getDouble("width", 1.0) <= 0
                    || place.getDouble("height", 1.0) <= 0) {
                result.failed("place width/height must be positive");
            }
            if (place.contains("seat") && place.getDouble("seat") < 0) {
                result.failed("place.seat must be >= 0");
            }
            int light = place.getInt("light", 0);
            if (light < 0 || light > 15) {
                result.failed("place.light must be within 0..15");
            }
            ConfigurationSection storage = place.getConfigurationSection("storage");
            if (place.contains("storage") && storage == null) {
                result.failed("place.storage must be a section");
            } else if (storage != null) {
                int rows = storage.getInt("rows", 3);
                if (rows < 1 || rows > 6) {
                    result.failed("place.storage.rows must be within 1..6");
                }
            }
            for (Object raw : place.getMapList("states")) {
                if (!(raw instanceof java.util.Map<?, ?> state)) {
                    result.failed("place.states entries must be mappings");
                    continue;
                }
                if (state.get("scale") instanceof Number scale
                        && scale.doubleValue() <= 0) {
                    result.failed("place.states[].scale must be positive");
                }
                if (state.get("light") instanceof Number value
                        && (value.intValue() < 0 || value.intValue() > 15)) {
                    result.failed("place.states[].light must be within 0..15");
                }
            }
        }

        return result;
    }
}
