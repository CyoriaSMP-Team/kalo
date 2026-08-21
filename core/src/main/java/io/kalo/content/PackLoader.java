package io.kalo.content;

import io.kalo.config.ConfigSchema;
import io.kalo.manager.RegistryManager;
import io.kalo.registry.Registries;
import io.kalo.registry.RegistriesImpl;
import io.kalo.utils.Files;
import io.kalo.utils.Plugins;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PackLoader {
    private static final ConfigSchema CONTENTS_PACK_CONFIG_SCHEMA = new ContentsPackConfigSchema();
    private static final ConfigSchema CONTENT_CONFIG_SCHEMA = new ContentConfigSchema();

    /** Parsed YAML is reused while size+mtime are unchanged; hot reload still replays type loaders. */
    private static final ConcurrentHashMap<Path, CachedYaml> YAML_CACHE = new ConcurrentHashMap<>();

    private PackLoader() {
    }

    public static @Nullable ContentsPack loadPack(@NotNull File folder) {
        return loadPack(folder, Plugins.logger(), ignored -> true);
    }

    /**
     * Lets the manager reject a duplicate id before any config type is allowed to mutate
     * its registry. This matters for registryless types such as recipes and sounds.
     */
    public static @Nullable ContentsPack loadPack(
            @NotNull File folder,
            @NotNull Predicate<String> acceptId
    ) {
        return loadPack(folder, Plugins.logger(), acceptId);
    }

    /** Logger-injected entry point keeps parsing independently testable without a live server. */
    static @Nullable ContentsPack loadPack(@NotNull File folder, @NotNull Logger logger) {
        return loadPack(folder, logger, ignored -> true);
    }

    static @Nullable ContentsPack loadPack(@NotNull File folder, @NotNull Logger logger,
                                           @NotNull Predicate<String> acceptId) {

        if (!folder.isDirectory()) {
            return null;
        }

        File settingsFile = new File(folder, "pack.yml");
        if (!settingsFile.isFile()) {
            logger.warning("Failed to load '" + folder.getName() + "' pack ( Missing pack.yml )");
            return null;
        }

        YamlConfiguration settingsConfig = loadYaml(settingsFile, logger);
        if (settingsConfig == null) {
            return null;
        }

        ConfigSchema.Result validationResult = CONTENTS_PACK_CONFIG_SCHEMA.validate(settingsConfig);
        if (!validationResult.isSuccess()) {
            logger.warning("Failed to load '" + folder.getName() + "' pack");
            validationResult.getErrors().forEach(error -> logger.warning("  " + error));
            return null;
        }

        String id = Objects.requireNonNull(settingsConfig.getString("id"));
        String version = Objects.requireNonNull(settingsConfig.getString("version"));
        String author = Objects.requireNonNull(settingsConfig.getString("author"));
        if (!acceptId.test(id)) {
            return null;
        }

        Registries registries = new RegistriesImpl();
        PackContext context = new PackContext(id, folder);

        File configsFolder = new File(folder, "configs");
        if (configsFolder.isDirectory()) {
            loadConfigs(context, configsFolder, registries, logger);
        } else if (configsFolder.exists()) {
            logger.warning("Ignoring " + configsFolder + " because 'configs' must be a directory");
        }

        return new ContentsPackImpl(id, version, author, folder, registries);
    }

    private static void loadConfigs(@NotNull PackContext pack, @NotNull File folder,
                                    @NotNull Registries registries, @NotNull Logger logger) {
        // Only YAML files are configs. Walking every regular file meant a stray .png in
        // configs/ was handed to the YAML parser.
        List<File> configFiles = Files.listFilesRecursively(folder, ".yml", ".yaml");

        for (File configFile : configFiles) {
            YamlConfiguration config = loadYaml(configFile, logger);
            if (config == null) {
                continue;
            }

            for (String key : config.getKeys(false)) {
                ConfigurationSection contentConfig = config.getConfigurationSection(key);
                if (contentConfig == null) {
                    logger.warning("Failed to load '" + key + "' in " + configFile
                            + " (content definition must be a configuration section)");
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
                        .get(ContentConfigSchema.resolveTypeKey(typeId))
                        .orElse(null);
                if (type == null) {
                    // Already reported by CONTENT_CONFIG_SCHEMA; reaching here means the
                    // type registry changed underneath us mid-load.
                    logger.warning("Content type '" + typeId + "' disappeared while loading " + key);
                    continue;
                }

                try {
                    if (!type.load(pack, registries, contentConfig)) {
                        logger.warning("Failed to load '" + contentConfig.getName() + "' in " + configFile);
                    }
                } catch (Exception e) {
                    // Third-party content types are allowed here too. One faulty type must
                    // not discard the rest of the pack, but its failure must name the exact
                    // declaration and file rather than disappearing inside the event call.
                    logger.log(Level.WARNING, "Failed to load '" + contentConfig.getName()
                            + "' in " + configFile, e);
                }
            }
        }
    }

    private static @Nullable YamlConfiguration loadYaml(@NotNull File file, @NotNull Logger logger) {
        Path path = file.toPath().toAbsolutePath().normalize();
        try {
            BasicFileAttributes attributes = java.nio.file.Files.readAttributes(path, BasicFileAttributes.class);
            CachedYaml cached = YAML_CACHE.get(path);
            if (cached != null && cached.size() == attributes.size()
                    && cached.modifiedMillis() == attributes.lastModifiedTime().toMillis()) {
                return cached.configuration();
            }

            YamlConfiguration config = new YamlConfiguration();
            config.load(file);
            YAML_CACHE.put(path, new CachedYaml(attributes.size(), attributes.lastModifiedTime().toMillis(), config));
            return config;
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not parse YAML file " + file, e);
            return null;
        }
    }

    private record CachedYaml(long size, long modifiedMillis, @NotNull YamlConfiguration configuration) {}
}
