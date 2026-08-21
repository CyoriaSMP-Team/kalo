package io.kalo.content;

import io.kalo.config.ConfigSchema;
import io.kalo.content.feature.FeatureFactory;
import io.kalo.manager.RegistryManager;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;

public final class ContentConfigSchema implements ConfigSchema {
    @Override
    public Result validate(ConfigurationSection config) {
        Result result = new Result();

        // The pack supplies the namespace, so a declaration is a key value rather than a
        // complete namespaced key. Accepting `other:thing` here only postpones the failure
        // until PackContext tries to put a colon in the value.
        if (!Key.parseableValue(config.getName())) {
            result.failed("Invalid content key '" + config.getName() + "'");
        }

        String typeId = config.getString("type");
        if (typeId == null) {
            result.failed("Missing type");
        } else {
            try {
                ContentType<?> type = RegistryManager.GlobalRegistries.registries().types()
                        .get(resolveTypeKey(typeId))
                        .orElse(null);
                if (type == null) {
                    result.failed("Unknown type '" + typeId + "'");
                }
            } catch (RuntimeException e) {
                result.failed("Invalid type id '" + typeId + "'");
            }
        }

        ConfigurationSection featuresSection = config.getConfigurationSection("features");
        if (config.contains("features") && featuresSection == null) {
            result.failed("features in " + config.getName() + " must be a configuration section");
        } else if (featuresSection != null) {
            for (String featureKey : featuresSection.getKeys(false)) {
                ConfigurationSection featureConfig = featuresSection.getConfigurationSection(featureKey);
                if (featureConfig == null) {
                    result.failed("Feature '" + featureKey + "' in " + config.getName()
                            + " must be a configuration section");
                    continue;
                }

                String featureId = featureConfig.getString("id");
                if (featureId == null) {
                    result.failed("Invalid feature configuration schema in " + config.getName());
                    continue;
                }
                if(!Key.parseable(featureId)) {
                    result.failed("Invalid feature id in " + config.getName());
                    continue;
                }

                FeatureFactory<?> factory = RegistryManager.GlobalRegistries.registries().features()
                        .get(Key.key(featureId))
                        .orElse(null);
                if(factory == null) {
                    result.failed("Unknown feature '" + featureId + "' in " + config.getName());
                    continue;
                }
            }
        }

        return result;
    }

    /**
     * Resolves built-ins ergonomically while leaving the namespace open to add-ons.
     *
     * <p>{@code item} remains shorthand for {@code kalo:item}; a qualified id such as
     * {@code myaddon:widget} is looked up exactly as registered.</p>
     */
    static Key resolveTypeKey(String typeId) {
        return typeId.indexOf(':') >= 0
                ? Key.key(typeId)
                : Key.key(Constants.PLUGIN_ID, typeId);
    }
}
