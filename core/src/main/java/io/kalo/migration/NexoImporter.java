package io.kalo.migration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Converts Nexo item and recipe configs.
 *
 * <p>Nexo is Oraxen's successor and inherited its config shape, so the conversion itself
 * is Oraxen's. What differs is naming: Nexo renamed the Oraxen-specific keys after the
 * fork ({@code oraxen_item} became {@code nexo_item}, and the {@code Pack} section is
 * lowercase {@code Pack}/{@code pack} depending on version).</p>
 *
 * <p>Rather than guess at every rename, this normalises the handful of keys that are known
 * to differ and then hands the file to the Oraxen converter. Anything Nexo added that
 * Oraxen never had falls through to the unknown-key reporting, which is what makes a
 * divergence visible instead of silently lost.</p>
 */
public final class NexoImporter implements Importer {

    private final OraxenFormatImporter oraxen = new OraxenFormatImporter();

    @Override
    public @NotNull String name() {
        return "Nexo";
    }

    @Override
    public int detect(@NotNull YamlConfiguration source) {
        // Scored above Oraxen so a Nexo-specific key wins when both would match.
        for (String key : source.getKeys(false)) {
            ConfigurationSection section = source.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (containsNexoKey(section)) {
                return 95;
            }
        }
        return 0;
    }

    private static boolean containsNexoKey(@NotNull ConfigurationSection section) {
        if (section.contains("nexo_item")) {
            return true;
        }
        for (String child : section.getKeys(false)) {
            ConfigurationSection nested = section.getConfigurationSection(child);
            if (nested != null && containsNexoKey(nested)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @NotNull String convert(@NotNull YamlConfiguration source,
                                   @NotNull String namespace,
                                   @NotNull ImportReport report) {
        YamlConfiguration normalised = normalise(source);
        report.warn("Read as Nexo, which shares Oraxen's config shape; "
                + "any Nexo-only setting is listed below rather than converted");

        return oraxen.convert(normalised, namespace, report);
    }

    /** Rewrites Nexo's renamed keys to their Oraxen equivalents on a structural copy. */
    static @NotNull YamlConfiguration normalise(@NotNull YamlConfiguration source) {
        YamlConfiguration normalised = new YamlConfiguration();
        copySection(source, normalised);
        return normalised;
    }

    private static void copySection(@NotNull ConfigurationSection source,
                                    @NotNull ConfigurationSection target) {
        for (String sourceKey : source.getKeys(false)) {
            String targetKey = switch (sourceKey) {
                case "nexo_item" -> "oraxen_item";
                case "nexo_type" -> "minecraft_type";
                default -> sourceKey;
            };
            if (target.getKeys(false).contains(targetKey)) {
                throw new IllegalArgumentException("Nexo config contains both '" + sourceKey
                        + "' and its normalised key '" + targetKey + "'");
            }

            ConfigurationSection child = source.getConfigurationSection(sourceKey);
            if (child != null) {
                copySection(child, target.createSection(targetKey));
            } else {
                // Scalar values are copied verbatim. In particular, text containing the
                // words "nexo_item:" is content, not a YAML key to rewrite.
                target.set(targetKey, source.get(sourceKey));
            }
        }
    }
}
