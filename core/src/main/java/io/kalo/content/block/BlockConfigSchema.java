package io.kalo.content.block;

import io.kalo.config.ConfigSchema;
import org.bukkit.configuration.ConfigurationSection;

public final class BlockConfigSchema implements ConfigSchema {

    @Override
    public Result validate(ConfigurationSection config) {
        Result result = new Result();

        ConfigurationSection model = config.getConfigurationSection("model");
        if (model == null) {
            // Unlike an item there is no vanilla appearance to fall back on, so a block
            // without a model would render as missing texture in the world.
            result.failed("Missing model section — declare 'cube_all', 'cube' or 'custom'");
        } else {
            boolean hasSource = model.contains("cube_all") || model.contains("cube") || model.contains("custom");
            if (!hasSource) {
                result.failed("model section must declare one of 'cube_all', 'cube' or 'custom'");
            }
        }

        ConfigurationSection behaviour = config.getConfigurationSection("behaviour");
        if (behaviour == null) {
            behaviour = config.getConfigurationSection("behavior");
        }
        if (behaviour != null && behaviour.contains("hardness")) {
            double hardness = behaviour.getDouble("hardness");
            if (hardness < 0 && hardness != -1d) {
                result.failed("hardness must be >= 0, or -1 for unbreakable; got " + hardness);
            }
        }

        return result;
    }
}
