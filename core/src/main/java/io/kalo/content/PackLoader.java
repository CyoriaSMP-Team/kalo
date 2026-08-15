package io.kalo.content;

import io.kalo.config.ConfigSchema;
import io.kalo.manager.RegistryManager;
import io.kalo.registry.Registries;
import io.kalo.registry.RegistriesImpl;
import io.kalo.utils.Constants;
import io.kalo.utils.Files;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public final class PackLoader {
    private static final ConfigSchema CONTENTS_PACK_CONFIG_SCHEMA = new ContentsPackConfigSchema();
    private static final ConfigSchema CONTENT_CONFIG_SCHEMA = new ContentConfigSchema();

    private PackLoader() {
    }

    public static @Nullable ContentsPack loadPack(@NotNull File folder) {
        Logger logger = Plugins.logger();

        if (!folder.isDirectory()) {
            return null;
        }

        File settingsFile = new File(folder, "pack.yml");
        if (!settingsFile.exists()) {
            logger.warning("Failed to load '" + folder.getName() + "' pack ( Missing pack.yml )");
            return null;
        }

        YamlConfiguration settingsConfig = YamlConfiguration.loadConfiguration(settingsFile);

        ConfigSchema.Result validationResult = CONTENTS_PACK_CONFIG_SCHEMA.validate(settingsConfig);
        if (!validationResult.isSuccess()) {
            logger.warning("Failed to load '" + folder.getName() + "' pack");
            validationResult.getErrors().forEach(error -> logger.warning("  " + error));
            return null;
        }

        String id = Objects.requireNonNull(settingsConfig.getString("id"));
        String version = Objects.requireNonNull(settingsConfig.getString("version"));
        String author = Objects.requireNonNull(settingsConfig.getString("author"));

        Registries registries = new RegistriesImpl();
        PackContext context = new PackContext(id, folder);

        File configsFolder = new File(folder, "configs");
        if (configsFolder.exists()) {
            loadConfigs(context, configsFolder, registries);
        }

        return new ContentsPackImpl(id, version, author, folder, registries);
    }

    private static void loadConfigs(@NotNull PackContext pack, @NotNull File folder, @NotNull Registries registries) {
        Logger logger = Plugins.logger();

        // Only YAML files are configs. Walking every regular file meant a stray .png in
        // configs/ was handed to the YAML parser.
        List<File> configFiles = Files.listFilesRecursively(folder, ".yml", ".yaml");

        for (File configFile : configFiles) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            for (String key : config.getKeys(false)) {
                ConfigurationSection contentConfig = config.getConfigurationSection(key);
                if (contentConfig == null) {
                    continue;
                }

                ConfigSchema.Result validationResult = CONTENT_CONFIG_SCHEMA.validate(contentConfig);
                if (!validationResult.isSuccess()) {
                    logger.warning("Failed to load '" + key + "' in " + configFile.getName());
                    validationResult.getErrors().forEach(error -> logger.warning("  " + error));
                    continue;
                }

                String typeId = Objects.requireNonNull(contentConfig.getString("type"));
                ContentType<?> type = RegistryManager.GlobalRegistries.registries().types()
                        .get(Key.key(Constants.PLUGIN_ID, typeId))
                        .orElse(null);
                if (type == null) {
                    // Already reported by CONTENT_CONFIG_SCHEMA; reaching here means the
                    // type registry changed underneath us mid-load.
                    logger.warning("Content type '" + typeId + "' disappeared while loading " + key);
                    continue;
                }

                if (!type.load(pack, registries, contentConfig)) {
                    logger.warning("Failed to load " + contentConfig.getName() + " in " + configFile.getName());
                }
            }
        }
    }
}
