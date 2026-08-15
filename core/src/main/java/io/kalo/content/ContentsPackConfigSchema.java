package io.kalo.content;

import io.kalo.config.ConfigSchema;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;

public final class ContentsPackConfigSchema implements ConfigSchema {

    @Override
    public Result validate(ConfigurationSection config) {
        Result result = new Result();

        String id = config.getString("id");
        if (id == null) {
            result.failed("Missing id");
        } else if (!Key.parseableNamespace(id)) {
            // The pack id becomes the namespace for every asset path and content key it
            // defines, so it has to be a legal namespace, not merely a legal key value.
            result.failed("Invalid id '" + id + "' — must be a valid namespace (a-z, 0-9, _, -, .)");
        }

        if (config.getString("version") == null) {
            result.failed("Missing version");
        }

        if (config.getString("author") == null) {
            result.failed("Missing author");
        }

        return result;
    }
}
