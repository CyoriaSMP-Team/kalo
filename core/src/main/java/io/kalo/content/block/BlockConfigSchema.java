package io.kalo.content.block;

import io.kalo.config.ConfigSchema;
import io.kalo.content.block.definition.BlockCarrier;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;

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
            int sources = (model.contains("cube_all") ? 1 : 0)
                    + (model.contains("cube") ? 1 : 0)
                    + (model.contains("custom") ? 1 : 0);
            if (sources == 0) {
                result.failed("model section must declare one of 'cube_all', 'cube' or 'custom'");
            } else if (sources > 1) {
                result.failed("model section must declare exactly one of 'cube_all', 'cube' or 'custom'");
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

        ConfigurationSection java = config.getConfigurationSection("java");
        if (java != null) {
            String mode = java.getString("mode", "native");
            if (mode == null || (!mode.equalsIgnoreCase("native") && !mode.equalsIgnoreCase("virtual"))) {
                result.failed("java.mode must be 'native' or 'virtual'; got " + mode);
            }

            String carrier = java.getString("carrier");
            if (carrier != null) {
                try {
                    BlockCarrier.fromId(carrier);
                } catch (IllegalArgumentException e) {
                    result.failed("java.carrier is not a supported carrier: " + carrier
                            + "; expected one of " + Arrays.toString(BlockCarrier.values()));
                }
            }
        }

        return result;
    }
}
